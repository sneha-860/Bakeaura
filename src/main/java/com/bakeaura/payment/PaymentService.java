package com.bakeaura.payment;

import com.bakeaura.order.Order;
import com.bakeaura.order.OrderItem;
import com.bakeaura.payment.Payment;
import com.bakeaura.enums.OrderStatus;
import com.bakeaura.enums.PaymentStatus;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.order.OrderRepository;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductRepository;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import com.bakeaura.websocket.OrderTrackingService;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
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

    private final RazorpayClient razorpayClient;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderTrackingService orderTrackingService;

    public PaymentService(RazorpayClient razorpayClient,
                          PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          ProductRepository productRepository,
                          UserRepository userRepository,
                          OrderTrackingService orderTrackingService) {
        this.razorpayClient = razorpayClient;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderTrackingService = orderTrackingService;
    }

    // Called by OrderService when creating an order
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

    public PaymentResponseDto getPaymentByOrderId(Long orderId, String userEmail) {
        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        User user = userRepository.findByEmail(userEmail)
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

    // Called by the webhook controller when Razorpay notifies us of a payment
    @Transactional
    public void handleWebhook(String payload, String razorpaySignature) {

        // Step 1: Verify the webhook signature
        if (!verifyWebhookSignature(payload, razorpaySignature)) {
            throw new BadRequestException("Invalid webhook signature");
        }

        // Step 2: Parse the JSON payload
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
                    // Payment record might not exist yet if webhook arrives before our save
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

        // Auto-confirm the order
        Order order = payment.getOrder();
        reduceStock(order);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        // Broadcast the status change via WebSocket
        orderTrackingService.broadcastStatusUpdate(order.getId(), OrderStatus.CONFIRMED);

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
            productRepository.save(product);
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

            // Cancel the order
            Order order = payment.getOrder();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            log.warn("Payment failed for order {}", order.getId());
        });
    }

    // ---- HMAC-SHA256 signature verification ----
    private boolean verifyWebhookSignature(String payload, String signature) {
        try {
            if (signature == null || signature.isBlank()) {
                return false;
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // Convert bytes to hex string
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
