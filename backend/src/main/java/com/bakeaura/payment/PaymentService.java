package com.bakeaura.payment;

import com.bakeaura.order.Order;
import com.bakeaura.order.OrderCreatedEvent;
import com.bakeaura.order.OrderItem;
import com.bakeaura.enums.OrderStatus;
import com.bakeaura.enums.PaymentStatus;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.order.OrderRepository;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import com.bakeaura.websocket.OrderTrackingService;
import com.bakeaura.notification.NotificationService;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
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
    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final UserRepository userRepository;
    private final OrderTrackingService orderTrackingService;
    private final NotificationService notificationService;

    public PaymentService(RazorpayClient razorpayClient,
                          PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          ProductService productService,
                          UserRepository userRepository,
                          OrderTrackingService orderTrackingService,
                          NotificationService notificationService) {
        this.razorpayClient = razorpayClient;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.userRepository = userRepository;
        this.orderTrackingService = orderTrackingService;
        this.notificationService = notificationService;
    }

    @EventListener
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        Order order = event.getOrder();
        String razorpayOrderId = createRazorpayOrder(order.getTotalAmount(), order.getId());
        order.setRazorpayOrderId(razorpayOrderId);
        orderRepository.save(order);
        createPendingPayment(order, razorpayOrderId);
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
            throw new RuntimeException("Payment gateway error: " + e.getMessage());
        }
    }

    public String createRazorpayOrderFallback(BigDecimal amount, Long internalOrderId, Throwable t) {
        log.error("Razorpay circuit breaker triggered for order {}. Cause: {}", internalOrderId, t.getMessage());
        throw new RuntimeException("Payment service is temporarily unavailable. Please try again in a moment.");
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
        return paymentRepository.count();
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

        String payload = request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId();
        if (!verifySignature(payload, request.getRazorpaySignature(), keySecret)) {
            throw new BadRequestException("Invalid payment signature");
        }

        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
            payment.setRazorpaySignature(request.getRazorpaySignature());
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);

            reduceStock(order);
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            orderTrackingService.broadcastStatusUpdate(order.getId(), OrderStatus.CONFIRMED);
            notifyIfUserPresent(order.getSeller(), "PAYMENT_CAPTURED", "Payment captured for order #" + order.getId(), order.getId());
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
            handlePaymentFailed(event, razorpaySignature);
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

        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseGet(() -> {
                    Order order = orderRepository.findByRazorpayOrderId(razorpayOrderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Order not found for razorpay_order_id: " + razorpayOrderId));
                    return Payment.builder()
                            .order(order)
                            .razorpayOrderId(razorpayOrderId)
                            .amount(order.getTotalAmount())
                            .status(PaymentStatus.PENDING)
                            .build();
                });

        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            log.info("Payment {} was already captured; ignoring duplicate webhook", razorpayPaymentId);
            return;
        }

        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(razorpaySignature);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        Order order = payment.getOrder();
        reduceStock(order);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        orderTrackingService.broadcastStatusUpdate(order.getId(), OrderStatus.CONFIRMED);
        notifyIfUserPresent(order.getSeller(), "PAYMENT_CAPTURED", "Payment captured for order #" + order.getId(), order.getId());

        log.info("Payment captured for order {}", order.getId());
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

            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            productService.saveProduct(product);
        }
    }

    private void handlePaymentFailed(JSONObject event, String razorpaySignature) {
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
            payment.setRazorpaySignature(razorpaySignature);
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            Order order = payment.getOrder();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            notifyIfUserPresent(order.getCustomer(), "PAYMENT_FAILED", "Payment failed for order #" + order.getId(), order.getId());

            log.warn("Payment failed for order {}", order.getId());
        });
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