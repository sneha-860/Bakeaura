package com.bakeaura.payment;

import com.bakeaura.cart.CartService;
import com.bakeaura.order.Order;
import com.bakeaura.order.OrderCancelledEvent;
import com.bakeaura.order.OrderCreatedEvent;
import com.bakeaura.order.OrderItem;
import com.bakeaura.order.OrderService;
import com.bakeaura.enums.OrderStatus;
import com.bakeaura.enums.PaymentStatus;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.exception.ServiceUnavailableException;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import com.bakeaura.notification.NotificationService;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Service
@Slf4j
public class PaymentService {

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    private final RazorpayClient razorpayClient;
    private final PaymentRepository paymentRepository;
    private final ProductService productService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final OrderService orderService;
    private final CartService cartService;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(RazorpayClient razorpayClient,
                          PaymentRepository paymentRepository,
                          ProductService productService,
                          UserRepository userRepository,
                          NotificationService notificationService,
                          OrderService orderService,
                          CartService cartService,
                          ApplicationEventPublisher eventPublisher) {
        this.razorpayClient = razorpayClient;
        this.paymentRepository = paymentRepository;
        this.productService = productService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.orderService = orderService;
        this.cartService = cartService;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        Order order = event.getOrder();
        String razorpayOrderId = createRazorpayOrder(order.getTotalAmount(), order.getId());
        // Sets razorpayOrderId + paymentStatus=PENDING on the Order and persists it.
        orderService.setRazorpayOrderId(order, razorpayOrderId);
        createPendingPayment(order, razorpayOrderId);
    }

    // Triggered when an order is cancelled (by customer, seller, or payment failure flow).
    // Checks whether the payment was already captured and, if so, initiates a Razorpay refund.
    @EventListener
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        Order order = event.getOrder();
        paymentRepository.findByOrder_Id(order.getId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.CAPTURED) {
                try {
                    JSONObject refundRequest = new JSONObject();
                    refundRequest.put("amount", toPaise(payment.getAmount()));
                    refundRequest.put("speed", "normal");
                    razorpayClient.payments.refund(payment.getRazorpayPaymentId(), refundRequest);

                    payment.setStatus(PaymentStatus.REFUNDED);
                    paymentRepository.save(payment);
                    orderService.markPaymentRefunded(order);
                    notifyIfUserPresent(order.getCustomer(), "REFUND_INITIATED",
                            "Order #" + order.getId() + " was cancelled. Your payment of ₹"
                                    + order.getTotalAmount()
                                    + " will be refunded within 5–7 business days.",
                            order.getId());
                    log.info("Refund initiated via Razorpay for order {} payment {}",
                            order.getId(), payment.getRazorpayPaymentId());
                } catch (RazorpayException e) {
                    log.error("Razorpay refund failed for order {} payment {}: {}",
                            order.getId(), payment.getRazorpayPaymentId(), e.getMessage());
                    notifyIfUserPresent(order.getCustomer(), "REFUND_FAILED",
                            "Order #" + order.getId() + " was cancelled but the refund could not be " +
                                    "processed automatically. Please contact support to request your refund.",
                            order.getId());
                }
            }
        });
    }

    @CircuitBreaker(name = "razorpay", fallbackMethod = "createRazorpayOrderFallback")
    @Transactional
    public String createRazorpayOrder(BigDecimal amount, Long internalOrderId) {
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", toPaise(amount));
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "order_" + internalOrderId);

            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = razorpayOrder.get("id");

            log.info("Created Razorpay order {} for internal order {}", razorpayOrderId, internalOrderId);
            return razorpayOrderId;

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage());
            throw new ServiceUnavailableException("Payment gateway error: " + e.getMessage());
        }
    }

    public String createRazorpayOrderFallback(BigDecimal amount, Long internalOrderId, Throwable t) {
        log.error("Razorpay circuit breaker triggered for order {}. Cause: {}", internalOrderId, t.getMessage());
        throw new ServiceUnavailableException("Payment service is temporarily unavailable. Please try again in a moment.");
    }

    @Transactional
    public Payment createPendingPayment(Order order, String razorpayOrderId) {
        Payment payment = Payment.builder()
                .order(order)
                .razorpayOrderId(razorpayOrderId)
                .amount(order.getTotalAmount())
                .status(PaymentStatus.PENDING)
                .build();

        return paymentRepository.save(payment);
    }

    public PaymentResponseDto getPaymentByOrderId(Long orderId, Long userId) {
        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = payment.getOrder();
        boolean isCustomer = order.getCustomer() != null && order.getCustomer().getId().equals(user.getId());
        boolean isSeller = order.getSeller() != null && order.getSeller().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == com.bakeaura.enums.Role.ADMIN;

        if (!isCustomer && !isSeller && !isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        return toResponse(payment);
    }

    public RazorpayConfigResponse getPaymentConfig() {
        return new RazorpayConfigResponse(keyId, "INR");
    }

    public long countPayments() {
        return paymentRepository.countByStatus(PaymentStatus.CAPTURED);
    }

    @Transactional
    public PaymentResponseDto verifyPayment(VerifyPaymentRequest request, Long userId) {
        Payment payment = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = payment.getOrder();
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        // Signature must be verified before any state change
        String payload = request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId();
        if (!verifySignature(payload, request.getRazorpaySignature(), keySecret)) {
            throw new BadRequestException("Invalid payment signature");
        }

        if (payment.getStatus() == PaymentStatus.PENDING) {

            // Guard: prevent capturing payment for a cancelled order
            if (order.getStatus() == OrderStatus.CANCELLED) {
                throw new BadRequestException(
                        "Order #" + order.getId() + " has been cancelled. " +
                        "If your payment was deducted, please contact support for a refund.");
            }

            payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
            payment.setRazorpaySignature(request.getRazorpaySignature());
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Decrement stock (within this transaction — optimistic lock on Product via @Version)
            reduceStock(order);

            // Publishes PaymentCapturedEvent → OrderService.handlePaymentCapture sets
            // paymentStatus=CAPTURED on Order and calls confirmOrderAfterPayment.
            eventPublisher.publishEvent(new PaymentCapturedEvent(this, order));

            // Remove only this seller's items from cart — items from other sellers are preserved
            if (order.getCustomer() != null && order.getSeller() != null) {
                cartService.clearItemsBySeller(order.getCustomer().getId(), order.getSeller().getId());
            }
        }

        return toResponse(payment);
    }

    @Transactional
    public void handleWebhook(String payload, String razorpaySignature) {
        if (!verifyWebhookSignature(payload, razorpaySignature)) {
            throw new BadRequestException("Invalid webhook signature");
        }

        JSONObject event = new JSONObject(payload);
        String eventType = event.getString("event");

        log.info("Received Razorpay webhook event: {}", eventType);

        if ("payment.captured".equals(eventType)) {
            handlePaymentCaptured(event, razorpaySignature);
        } else if ("payment.failed".equals(eventType)) {
            handlePaymentFailed(event);
        } else {
            log.info("Ignoring unsupported Razorpay webhook event: {}", eventType);
        }
    }

    private void handlePaymentCaptured(JSONObject event, String razorpaySignature) {
        JSONObject paymentEntity = event
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String razorpayOrderId = paymentEntity.getString("order_id");
        String razorpayPaymentId = paymentEntity.getString("id");

        // Pessimistic lock on the Payment row serializes concurrent verifyPayment + webhook calls.
        // If verifyPayment committed first, this read sees CAPTURED and the guard below returns early.
        Payment payment = paymentRepository.findWithLockByRazorpayOrderId(razorpayOrderId)
                .orElseGet(() -> {
                    Order order = orderService.findOrderByRazorpayOrderId(razorpayOrderId);
                    return Payment.builder()
                            .order(order)
                            .razorpayOrderId(razorpayOrderId)
                            .amount(order.getTotalAmount())
                            .status(PaymentStatus.PENDING)
                            .build();
                });

        // Idempotency guard: only process a PENDING payment
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} is in status {}; ignoring webhook", razorpayPaymentId, payment.getStatus());
            return;
        }

        Order order = payment.getOrder();

        // Edge case: payment captured for an already-cancelled order.
        // Record the capture but do NOT confirm the order — customer must contact support for refund.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            payment.setRazorpayPaymentId(razorpayPaymentId);
            payment.setRazorpaySignature(razorpaySignature);
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);
            orderService.updatePaymentStatus(order, PaymentStatus.CAPTURED);
            notifyIfUserPresent(order.getCustomer(), "REFUND_REQUIRED",
                    "A payment of ₹" + order.getTotalAmount() + " was received for cancelled order #"
                            + order.getId() + ". Please contact support for a refund.",
                    order.getId());
            log.warn("Payment {} captured for cancelled order {}. Manual refund required.",
                    razorpayPaymentId, order.getId());
            return;
        }

        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(razorpaySignature);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        reduceStock(order);
        eventPublisher.publishEvent(new PaymentCapturedEvent(this, order));

        // Remove only this seller's items from cart — items from other sellers are preserved
        if (order.getCustomer() != null && order.getSeller() != null) {
            cartService.clearItemsBySeller(order.getCustomer().getId(), order.getSeller().getId());
        }

        log.info("Payment captured and order {} confirmed via webhook", order.getId());
    }

    private void handlePaymentFailed(JSONObject event) {
        JSONObject paymentEntity = event
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String razorpayOrderId = paymentEntity.getString("order_id");
        String razorpayPaymentId = paymentEntity.optString("id", null);

        paymentRepository.findByRazorpayOrderId(razorpayOrderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.CAPTURED) {
                log.info("Ignoring failed webhook for already captured payment {}", razorpayOrderId);
                return;
            }

            payment.setRazorpayPaymentId(razorpayPaymentId);
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            Order order = payment.getOrder();
            // Cancels the order, sets paymentStatus=FAILED, broadcasts WebSocket, notifies seller.
            orderService.markOrderCancelledByPaymentFailure(order);

            notifyIfUserPresent(order.getCustomer(), "PAYMENT_FAILED",
                    "Payment failed for order #" + order.getId() + ". No charge was made.",
                    order.getId());

            log.warn("Payment failed for order {}", order.getId());
        });
    }

    private void reduceStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();

            if (product.getStockQuantity() == null) {
                continue;
            }

            if (item.getQuantity() > product.getStockQuantity()) {
                throw new BadRequestException("Insufficient stock for product " + product.getId());
            }

            int newStock = product.getStockQuantity() - item.getQuantity();
            product.setStockQuantity(Math.max(newStock, 0));
            // Auto-hide the product when stock reaches zero so customers cannot add it to cart.
            if (product.getStockQuantity() == 0) {
                product.setIsAvailable(false);
            }
            productService.saveProduct(product);
        }
    }

    private boolean verifyWebhookSignature(String payload, String signature) {
        return verifySignature(payload, signature, webhookSecret);
    }

    private boolean verifySignature(String payload, String signature, String secret) {
        try {
            if (signature == null || signature.isBlank()) {
                return false;
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return MessageDigest.isEqual(
                    hexString.toString().getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );

        } catch (Exception e) {
            log.error("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    int toPaise(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private void notifyIfUserPresent(User user, String type, String message, Long relatedId) {
        if (user != null && user.getEmail() != null) {
            notificationService.notifyUser(user.getId(), type, message, relatedId);
        }
    }

    private PaymentResponseDto toResponse(Payment payment) {
        return PaymentResponseDto.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .razorpayOrderId(payment.getRazorpayOrderId())
                .razorpayPaymentId(payment.getRazorpayPaymentId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .createdAt(payment.getCreatedAt())
                .paidAt(payment.getPaidAt())
                .build();
    }
}
