package com.bakeaura.order;

import com.bakeaura.map.MapService;
import com.bakeaura.cart.CartDto;
import com.bakeaura.cart.CartItemDto;
import com.bakeaura.cart.CartService;
import com.bakeaura.notification.NotificationService;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductService;
import com.bakeaura.referral.ReferralOrderService;
import com.bakeaura.user.User;
import com.bakeaura.enums.OrderStatus;
import com.bakeaura.enums.OrderType;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.payment.PaymentService;
import com.bakeaura.user.UserRepository;
import com.bakeaura.websocket.OrderTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final UserRepository userRepository;
    private final MapService mapService;
    private final PaymentService paymentService;
    private final ReferralOrderService referralOrderService;
    private final OrderTrackingService orderTrackingService;
    private final CartService cartService;
    private final NotificationService notificationService;

    @Transactional
    public OrderResponseDto createOrder(CreateOrderRequestDto request, String customerEmail) {

        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        User seller = userRepository.findById(request.getSellerId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));

        if (!seller.getRole().equals(Role.SELLER)) {
            throw new BadRequestException("Target user is not a seller");
        }

        validateLocation(seller.getLatitude(), seller.getLongitude(), "Seller location is not configured");
        validateLocation(request.getDeliveryLatitude(), request.getDeliveryLongitude(), "Delivery location is required");

        double distanceKm = mapService.calculateEstimatedRoadDistance(
                seller.getLatitude(), seller.getLongitude(),
                request.getDeliveryLatitude(), request.getDeliveryLongitude()
        );

        if (!mapService.isWithinDeliveryRadius(distanceKm)) {
            throw new BadRequestException("Delivery address is outside the seller's delivery radius");
        }

        if (request.getOrderType() == OrderType.SCHEDULED) {
            if (request.getScheduledDeliveryDate() == null) {
                throw new BadRequestException("Scheduled delivery date is required for scheduled orders");
            }
            for (CreateOrderRequestDto.OrderItemRequest itemReq : request.getItems()) {
                Product product = productService.getProductEntityById(itemReq.getProductId());
                if (product.getMinAdvanceDays() != null) {
                    LocalDate earliestDate = LocalDate.now().plusDays(product.getMinAdvanceDays());
                    if (request.getScheduledDeliveryDate().isBefore(earliestDate)) {
                        throw new BadRequestException("Product " + product.getName() +
                                " requires at least " + product.getMinAdvanceDays() + " days advance notice");
                    }
                }
            }
        }

        Order order = Order.builder()
                .customer(customer)
                .seller(seller)
                .status(OrderStatus.PENDING)
                .deliveryAddress(request.getDeliveryAddress())
                .deliveryLatitude(request.getDeliveryLatitude())
                .deliveryLongitude(request.getDeliveryLongitude())
                .orderType(request.getOrderType())
                .scheduledDeliveryDate(request.getScheduledDeliveryDate())
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (CreateOrderRequestDto.OrderItemRequest itemReq : request.getItems()) {
            Product product = productService.getProductEntityById(itemReq.getProductId());
            if (!product.getSeller().getId().equals(seller.getId())) {
                throw new BadRequestException("Product " + product.getId() +
                        " does not belong to the seller");
            }
            validateProductForOrder(product, itemReq.getQuantity());

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .priceAtPurchase(product.getPrice())
                    .build();

            order.addItem(item);
            total = total.add(item.getSubtotal());
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);

        String razorpayOrderId = paymentService.createRazorpayOrder(total, saved.getId());
        saved.setRazorpayOrderId(razorpayOrderId);
        paymentService.createPendingPayment(saved, razorpayOrderId);

        if (request.getReferralCode() != null && !request.getReferralCode().isBlank()) {
            referralOrderService.processReferral(saved.getId(), request.getReferralCode(), total);
        }

        notificationService.notifyUser(
                seller.getEmail(),
                "ORDER_CREATED",
                "New order #" + saved.getId() + " has been placed.",
                saved.getId()
        );

        return toResponse(orderRepository.save(saved));
    }

    @Transactional
    public OrderResponseDto createOrderFromCart(CreateOrderFromCartRequestDto request,
                                                String customerEmail) {
        CartDto cart = cartService.getCartWithoutSync(customerEmail);
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        CreateOrderRequestDto orderRequest = new CreateOrderRequestDto();
        orderRequest.setSellerId(request.getSellerId());
        orderRequest.setDeliveryAddress(request.getDeliveryAddress());
        orderRequest.setDeliveryLatitude(request.getDeliveryLatitude());
        orderRequest.setDeliveryLongitude(request.getDeliveryLongitude());
        orderRequest.setOrderType(request.getOrderType());
        orderRequest.setScheduledDeliveryDate(request.getScheduledDeliveryDate());
        orderRequest.setReferralCode(request.getReferralCode());
        orderRequest.setItems(cart.getItems().stream()
                .map(this::toOrderItemRequest)
                .toList());

        OrderResponseDto response = createOrder(orderRequest, customerEmail);
        cartService.clearCart(customerEmail);
        return response;
    }

    @Transactional
    public OrderResponseDto updateStatus(Long orderId, OrderStatus newStatus, String userEmail) {

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isSeller = order.getSeller().getId().equals(user.getId());
        boolean isAdmin = user.getRole().equals(Role.ADMIN);

        if (!isSeller && !isAdmin) {
            throw new AccessDeniedException("You are not authorised to update this order");
        }

        validateTransition(order.getStatus(), newStatus);
        order.setStatus(newStatus);

        if (newStatus == OrderStatus.CONFIRMED) {
            validateLocation(order.getSeller().getLatitude(),
                    order.getSeller().getLongitude(), "Seller location is not configured");
            validateLocation(order.getDeliveryLatitude(),
                    order.getDeliveryLongitude(), "Delivery location is not configured");

            Integer eta = mapService.getEstimatedDeliveryMinutes(
                    order.getSeller().getLatitude(), order.getSeller().getLongitude(),
                    order.getDeliveryLatitude(), order.getDeliveryLongitude()
            );
            order.setEstimatedDeliveryMinutes(eta);
        }

        Order updated = orderRepository.save(order);

        orderTrackingService.broadcastStatusUpdate(orderId, newStatus);
        notificationService.notifyUser(
                order.getCustomer().getEmail(),
                "ORDER_STATUS",
                "Order #" + orderId + " status changed to " + newStatus,
                orderId
        );

        return toResponse(updated);
    }

    public Page<OrderResponseDto> getMyOrders(String customerEmail, int page, int size) {
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return orderRepository
                .findByCustomer_IdOrderByCreatedAtDesc(customer.getId(), pageable)
                .map(this::toResponse);
    }

    public Page<OrderResponseDto> getSellerOrders(String sellerEmail, OrderStatus status,
                                                  int page, int size) {
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Order> orders = status == null
                ? orderRepository.findBySeller_IdOrderByCreatedAtDesc(seller.getId(), pageable)
                : orderRepository.findBySeller_IdAndStatusOrderByCreatedAtDesc(
                seller.getId(), status, pageable);
        return orders.map(this::toResponse);
    }

    public OrderResponseDto getOrderById(Long orderId, String userEmail) {
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

    @Transactional
    public OrderResponseDto cancelOrder(Long orderId, String customerEmail) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("Access denied");
        }
        if (order.getStatus() != OrderStatus.PENDING &&
                order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order cannot be cancelled at this stage");
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order updated = orderRepository.save(order);
        orderTrackingService.broadcastStatusUpdate(orderId, OrderStatus.CANCELLED);
        notificationService.notifyUser(
                order.getSeller().getEmail(),
                "ORDER_CANCELLED",
                "Order #" + orderId + " was cancelled by the customer.",
                orderId
        );
        return toResponse(updated);
    }

    private CreateOrderRequestDto.OrderItemRequest toOrderItemRequest(CartItemDto cartItem) {
        CreateOrderRequestDto.OrderItemRequest item = new CreateOrderRequestDto.OrderItemRequest();
        item.setProductId(cartItem.getProductId());
        item.setQuantity(cartItem.getQuantity());
        return item;
    }

    private void validateTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case PENDING -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PREPARING || next == OrderStatus.CANCELLED;
            case PREPARING -> next == OrderStatus.OUT_FOR_DELIVERY || next == OrderStatus.CANCELLED;
            case OUT_FOR_DELIVERY -> next == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };

        if (!valid) {
            throw new BadRequestException(
                    "Cannot transition from " + current + " to " + next);
        }
    }

    private void validateLocation(Double latitude, Double longitude, String message) {
        if (latitude == null || longitude == null) {
            throw new BadRequestException(message);
        }
    }

    private void validateProductForOrder(Product product, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Quantity must be at least 1");
        }
        if (!Boolean.TRUE.equals(product.getIsAvailable())) {
            throw new BadRequestException("Product " + product.getId() + " is not available");
        }
        if (product.getStockQuantity() != null && quantity > product.getStockQuantity()) {
            throw new BadRequestException(
                    "Requested quantity exceeds available stock for product " + product.getId());
        }
    }

    private OrderResponseDto toResponse(Order order) {
        List<OrderResponseDto.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderResponseDto.OrderItemResponse.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .priceAtPurchase(item.getPriceAtPurchase())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponseDto.builder()
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