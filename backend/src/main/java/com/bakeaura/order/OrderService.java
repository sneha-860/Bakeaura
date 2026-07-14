package com.bakeaura.order;

import com.bakeaura.map.MapService;
import com.bakeaura.cart.CartDto;
import com.bakeaura.cart.CartItemDto;
import com.bakeaura.cart.CartService;
import com.bakeaura.enums.PaymentStatus;
import com.bakeaura.notification.EmailService;
import com.bakeaura.notification.SmsService;
import com.bakeaura.notification.NotificationService;
import com.bakeaura.payment.PaymentCapturedEvent;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductService;
import com.bakeaura.user.User;
import com.bakeaura.enums.OrderStatus;
import com.bakeaura.enums.OrderType;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.user.UserRepository;
import com.bakeaura.websocket.OrderTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
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
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final UserRepository userRepository;
    private final MapService mapService;
    private final CartService cartService;
    private final NotificationService notificationService;
    private final OrderTrackingService orderTrackingService;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailService emailService;
    private final SmsService smsService;

    @Transactional
    public OrderResponseDto createOrder(CreateOrderRequestDto request, Long customerId) {

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        if (!Boolean.TRUE.equals(customer.getIsEmailVerified())) {
            throw new BadRequestException("Please verify your email address before placing orders");
        }

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

        if (request.getOrderType() == OrderType.SCHEDULED && request.getScheduledDeliveryDate() == null) {
            throw new BadRequestException("Scheduled delivery date is required for scheduled orders");
        }

        Order order = Order.builder()
                .customer(customer)
                .seller(seller)
                .status(OrderStatus.PENDING)
                .deliveryAddress(request.getDeliveryAddress())
                .deliveryLatitude(request.getDeliveryLatitude())
                .deliveryLongitude(request.getDeliveryLongitude())
                .deliveryInstructions(request.getDeliveryInstructions())
                .orderType(request.getOrderType())
                .scheduledDeliveryDate(request.getScheduledDeliveryDate())
                .referralCode(request.getReferralCode())
                .build();

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Order must contain at least one item");
        }

        BigDecimal total = BigDecimal.ZERO;

        for (CreateOrderRequestDto.OrderItemRequest itemReq : request.getItems()) {
            Product product = productService.getProductEntityById(itemReq.getProductId());
            if (!product.getSeller().getId().equals(seller.getId())) {
                throw new BadRequestException("Product " + product.getId() +
                        " does not belong to the seller");
            }
            validateProductForOrder(product, itemReq.getQuantity(), request.getOrderType(), request.getScheduledDeliveryDate());

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

        // Publishes to: PaymentService (creates Razorpay order + payment record)
        //               OrderTrackingService (broadcasts PENDING status to order WebSocket)
        // Seller notification fires only after payment is captured via confirmOrderAfterPayment.
        eventPublisher.publishEvent(
                new OrderCreatedEvent(this, saved, customer.getEmail(), request.getReferralCode())
        );

        return toResponse(saved);
    }

    @Transactional
    public OrderResponseDto createOrderFromCart(CreateOrderFromCartRequestDto request,
                                                Long customerId) {
        CartDto cart = cartService.getCartWithoutSync(customerId);
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
        orderRequest.setDeliveryInstructions(request.getDeliveryInstructions());
        orderRequest.setItems(cart.getItems().stream()
                .filter(item -> request.getSellerId().equals(item.getSellerId()))
                .map(this::toOrderItemRequest)
                .toList());

        return createOrder(orderRequest, customerId);
    }

    /**
     * Called by PaymentService after a payment is successfully captured (both client-side verify
     * and webhook path). Sets order to CONFIRMED, calculates ETA, sends confirmation email/SMS,
     * notifies both seller and customer, and broadcasts the status update via WebSocket.
     *
     * Must NOT be called for cancelled orders — PaymentService checks order.status before calling.
     */
    @Transactional
    public void confirmOrderAfterPayment(Order order) {
        try {
            validateLocation(order.getSeller().getLatitude(), order.getSeller().getLongitude(),
                    "Seller location not configured");
            validateLocation(order.getDeliveryLatitude(), order.getDeliveryLongitude(),
                    "Delivery location not configured");
            Integer eta = mapService.getEstimatedDeliveryMinutes(
                    order.getSeller().getLatitude(), order.getSeller().getLongitude(),
                    order.getDeliveryLatitude(), order.getDeliveryLongitude()
            );
            order.setEstimatedDeliveryMinutes(eta);
        } catch (Exception e) {
            log.warn("Could not calculate ETA for order {}: {}", order.getId(), e.getMessage());
        }

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        orderTrackingService.broadcastStatusUpdate(order.getId(), OrderStatus.CONFIRMED);

        // Notify seller — this is the first time seller sees the order (payment is confirmed)
        notificationService.notifyUser(
                order.getSeller().getId(),
                "NEW_ORDER",
                "New paid order #" + order.getId() + " from " + order.getCustomer().getName()
                        + " · ₹" + order.getTotalAmount(),
                order.getId()
        );

        // Notify customer
        notificationService.notifyUser(
                order.getCustomer().getId(),
                "PAYMENT_CONFIRMED",
                "Payment confirmed for order #" + order.getId() + ". Your baker has received your order!",
                order.getId()
        );

        // Email and SMS are @Async — return immediately, fire in background
        emailService.sendOrderConfirmationEmail(
                order.getCustomer().getEmail(),
                String.valueOf(order.getId()),
                order.getSeller().getName()
        );
        smsService.sendOrderConfirmedSms(
                order.getCustomer().getPhone(),
                String.valueOf(order.getId())
        );
    }

    @Transactional
    public OrderResponseDto updateStatus(Long orderId, OrderStatus newStatus, Long userId) {

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isSeller = order.getSeller().getId().equals(user.getId());
        boolean isAdmin = user.getRole().equals(Role.ADMIN);

        if (!isSeller && !isAdmin) {
            throw new AccessDeniedException("You are not authorised to update this order");
        }

        // Sellers cannot cancel PENDING orders — the customer may be mid-payment.
        // Only the customer (via cancelOrder) can cancel at PENDING stage.
        if (isSeller && !isAdmin
                && order.getStatus() == OrderStatus.PENDING
                && newStatus == OrderStatus.CANCELLED) {
            throw new BadRequestException(
                    "Cannot cancel a pending order — the customer may be completing payment. " +
                    "Wait for the order to be confirmed before cancelling.");
        }

        validateTransition(order.getStatus(), newStatus);
        order.setStatus(newStatus);

        if (newStatus == OrderStatus.DELIVERED) {
            emailService.sendOrderDeliveredEmail(
                    order.getCustomer().getEmail(),
                    String.valueOf(order.getId())
            );
        }

        if (newStatus == OrderStatus.OUT_FOR_DELIVERY) {
            smsService.sendOutForDeliverySms(
                    order.getCustomer().getPhone(),
                    String.valueOf(order.getId())
            );
        }

        Order updated = orderRepository.save(order);

        orderTrackingService.broadcastStatusUpdate(orderId, newStatus);
        notificationService.notifyUser(
                order.getCustomer().getId(),
                "ORDER_STATUS",
                "Order #" + orderId + " is now " + newStatus.toString().toLowerCase().replace("_", " "),
                orderId
        );

        // Fire refund check after the order is saved so PaymentService sees the CANCELLED state.
        if (newStatus == OrderStatus.CANCELLED) {
            eventPublisher.publishEvent(new OrderCancelledEvent(this, updated));
        }

        return toResponse(updated);
    }

    public Page<OrderResponseDto> getMyOrders(Long customerId, int page, int size) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return orderRepository
                .findByCustomer_IdOrderByCreatedAtDesc(customer.getId(), pageable)
                .map(this::toResponse);
    }

    public Page<OrderResponseDto> getSellerOrders(Long sellerId, OrderStatus status,
                                                  int page, int size) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Order> orders = status == null
                ? orderRepository.findBySeller_IdOrderByCreatedAtDesc(seller.getId(), pageable)
                : orderRepository.findBySeller_IdAndStatusOrderByCreatedAtDesc(
                seller.getId(), status, pageable);
        return orders.map(this::toResponse);
    }

    public OrderResponseDto getOrderById(Long orderId, Long userId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isCustomer = order.getCustomer().getId().equals(user.getId());
        boolean isSeller = order.getSeller().getId().equals(user.getId());
        boolean isAdmin = user.getRole().equals(Role.ADMIN);

        if (!isCustomer && !isSeller && !isAdmin) {
            throw new ResourceNotFoundException("Order not found");
        }

        return toResponse(order);
    }

    @Transactional
    public OrderResponseDto cancelOrder(Long orderId, Long customerId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        User customer = userRepository.findById(customerId)
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
                order.getSeller().getId(),
                "ORDER_CANCELLED",
                "Order #" + orderId + " was cancelled by the customer.",
                orderId
        );
        eventPublisher.publishEvent(new OrderCancelledEvent(this, updated));
        return toResponse(updated);
    }

    public long countOrders() {
        return orderRepository.countByStatusNot(OrderStatus.CANCELLED);
    }

    public List<Order> getOrdersBySellerForAnalytics(Long sellerId) {
        return orderRepository.findBySeller_IdOrderByCreatedAtDesc(sellerId);
    }

    // ── Methods exposed for PaymentService (boundary-safe delegation) ─────────

    public Order getOrderEntityById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    public Order findOrderByRazorpayOrderId(String razorpayOrderId) {
        return orderRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found for razorpay_order_id: " + razorpayOrderId));
    }

    @Transactional
    public void setRazorpayOrderId(Order order, String razorpayOrderId) {
        order.setRazorpayOrderId(razorpayOrderId);
        order.setPaymentStatus(PaymentStatus.PENDING);
        orderRepository.save(order);
    }

    @Transactional
    public void updatePaymentStatus(Order order, PaymentStatus paymentStatus) {
        order.setPaymentStatus(paymentStatus);
        orderRepository.save(order);
    }

    @Transactional
    public void markPaymentRefunded(Order order) {
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        orderRepository.save(order);
    }

    @Transactional
    public void markOrderCancelledByPaymentFailure(Order order) {
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.FAILED);
        orderRepository.save(order);
        orderTrackingService.broadcastStatusUpdate(order.getId(), OrderStatus.CANCELLED);
        notificationService.notifyUser(
                order.getSeller().getId(),
                "ORDER_CANCELLED",
                "Order #" + order.getId() + " was automatically cancelled due to payment failure.",
                order.getId()
        );
    }

    @EventListener
    public void handlePaymentCapture(PaymentCapturedEvent event) {
        Order order = event.getOrder();
        order.setPaymentStatus(PaymentStatus.CAPTURED);
        confirmOrderAfterPayment(order);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private CreateOrderRequestDto.OrderItemRequest toOrderItemRequest(CartItemDto cartItem) {
        CreateOrderRequestDto.OrderItemRequest item = new CreateOrderRequestDto.OrderItemRequest();
        item.setProductId(cartItem.getProductId());
        item.setQuantity(cartItem.getQuantity());
        return item;
    }

    private void validateTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            // PENDING → CONFIRMED is intentionally removed: only PaymentService sets CONFIRMED.
            // Sellers receive orders only after payment capture, so their first action is PREPARING.
            case PENDING -> next == OrderStatus.CANCELLED;
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

    private void validateProductForOrder(Product product, Integer quantity, OrderType orderType, LocalDate scheduledDeliveryDate) {
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
        if (Boolean.TRUE.equals(product.getIsPreOrderOnly()) && orderType != OrderType.SCHEDULED) {
            throw new BadRequestException("Product " + product.getName() +
                    " is a pre-order only item and requires a scheduled order");
        }
        if (product.getMinAdvanceDays() != null && product.getMinAdvanceDays() > 0) {
            if (orderType == OrderType.INSTANT) {
                throw new BadRequestException("Product " + product.getName() +
                        " requires " + product.getMinAdvanceDays() + " days advance notice and cannot be ordered instantly");
            }
            LocalDate earliestDate = LocalDate.now().plusDays(product.getMinAdvanceDays());
            if (scheduledDeliveryDate == null || scheduledDeliveryDate.isBefore(earliestDate)) {
                throw new BadRequestException("Product " + product.getName() +
                        " requires at least " + product.getMinAdvanceDays() + " days advance notice");
            }
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

        PaymentStatus paymentStatus = order.getPaymentStatus();

        return OrderResponseDto.builder()
                .id(order.getId())
                .sellerId(order.getSeller().getId())
                .customerName(order.getCustomer().getName())
                .sellerName(order.getSeller().getName())
                .status(order.getStatus())
                .paymentStatus(paymentStatus)
                .totalAmount(order.getTotalAmount())
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryInstructions(order.getDeliveryInstructions())
                .estimatedDeliveryMinutes(order.getEstimatedDeliveryMinutes())
                .razorpayOrderId(order.getRazorpayOrderId())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
