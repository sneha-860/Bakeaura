package com.bakeaura.order;


import com.bakeaura.order.CreateOrderRequest;
import com.bakeaura.order.OrderResponse;
import com.bakeaura.order.Order;
import com.bakeaura.order.OrderItem;
import com.bakeaura.product.Product;
import com.bakeaura.user.User;
import com.bakeaura.common.OrderStatus;
import com.bakeaura.common.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.order.OrderRepository;
import com.bakeaura.product.ProductRepository;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final MapService mapService;
    private final PaymentService paymentService;
    private final OrderTrackingService orderTrackingService;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String customerEmail) {

        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        User seller = userRepository.findById(request.getSellerId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));

        if (!seller.getRole().equals(Role.SELLER)) {
            throw new BadRequestException("Target user is not a seller");
        }

        // ---- Validate delivery radius ----
        // Get seller's location (assumes seller entity has latitude/longitude)
        double distanceKm = mapService.calculateDistance(
                seller.getLatitude(), seller.getLongitude(),
                request.getDeliveryLatitude(), request.getDeliveryLongitude()
        );

        if (!mapService.isWithinDeliveryRadius(distanceKm)) {
            throw new BadRequestException("Delivery address is outside the seller's delivery radius");
        }

        // ---- Build order ----
        Order order = Order.builder()
                .customer(customer)
                .seller(seller)
                .status(OrderStatus.PENDING)
                .deliveryAddress(request.getDeliveryAddress())
                .deliveryLatitude(request.getDeliveryLatitude())
                .deliveryLongitude(request.getDeliveryLongitude())
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product " + itemReq.getProductId() + " not found"));

            // Ensure product belongs to this seller
            if (!product.getSeller().getId().equals(seller.getId())) {
                throw new BadRequestException("Product " + product.getId() + " does not belong to the seller");
            }

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .priceAtPurchase(product.getPrice())
                    .build();

            order.addItem(item);
            total = total.add(item.getSubtotal());
        }

        order.setTotalAmount(total);

        // ---- Create Razorpay order ----
        String razorpayOrderId = paymentService.createRazorpayOrder(total, order.getId());
        order.setRazorpayOrderId(razorpayOrderId);

        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus newStatus, String userEmail) {

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Only the seller of this order or an ADMIN can update status
        boolean isSeller = order.getSeller().getId().equals(user.getId());
        boolean isAdmin = user.getRole().equals(Role.ADMIN);

        if (!isSeller && !isAdmin) {
            throw new AccessDeniedException("You are not authorised to update this order");
        }

        validateTransition(order.getStatus(), newStatus);
        order.setStatus(newStatus);

        // If confirming, set estimated delivery time
        if (newStatus == OrderStatus.CONFIRMED) {
            Integer eta = mapService.getEstimatedDeliveryMinutes(
                    order.getSeller().getLatitude(), order.getSeller().getLongitude(),
                    order.getDeliveryLatitude(), order.getDeliveryLongitude()
            );
            order.setEstimatedDeliveryMinutes(eta);
        }

        Order updated = orderRepository.save(order);

        // Broadcast status update via WebSocket to all subscribers of this order
        orderTrackingService.broadcastStatusUpdate(orderId, newStatus);

        return toResponse(updated);
    }

    public List<OrderResponse> getMyOrders(String customerEmail) {
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return orderRepository.findByCustomer_IdOrderByCreatedAtDesc(customer.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<OrderResponse> getSellerOrders(String sellerEmail) {
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return orderRepository.findBySeller_IdOrderByCreatedAtDesc(seller.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public OrderResponse getOrderById(Long orderId, String userEmail) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isCustomer = order.getCustomer().getId().equals(user.getId());
        boolean isSeller = order.getSeller().getId().equals(user.getId());
        boolean isAdmin = user.getRole().equals(Role.ADMIN);

        if (!isCustomer && !isSeller && !isAdmin) {
            throw new AccessDeniedException("Access denied");
        }

        return toResponse(order);
    }

    // ---- State machine: enforce valid transitions ----
    private void validateTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case PENDING -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PREPARING || next == OrderStatus.CANCELLED;
            case PREPARING -> next == OrderStatus.OUT_FOR_DELIVERY || next == OrderStatus.CANCELLED;
            case OUT_FOR_DELIVERY -> next == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;  // Terminal states
        };

        if (!valid) {
            throw new BadRequestException(
                    "Cannot transition from " + current + " to " + next
            );
        }
    }

    // ---- Mapper ----
    private OrderResponse toResponse(Order order) {
        List<OrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderResponse.OrderItemResponse.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .priceAtPurchase(item.getPriceAtPurchase())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .customerName(order.getCustomer().getName())
                .sellerName(order.getSeller().getName())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .deliveryAddress(order.getDeliveryAddress())
                .estimatedDeliveryMinutes(order.getEstimatedDeliveryMinutes())
                .razorpayOrderId(order.getRazorpayOrderId())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
