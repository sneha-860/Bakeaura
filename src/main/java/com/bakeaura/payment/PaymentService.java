package com.bakeaura.payment;

import com.bakeaura.order.Order;
import com.bakeaura.payment.Payment;
import com.bakeaura.enums.OrderStatus;
import com.bakeaura.enums.PaymentStatus;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.order.OrderRepository;
import com.bakeaura.payment.PaymentRepository;
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
import java.time.LocalDateTime;

@Service
@Slf4j
public class PaymentService {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderTrackingService orderTrackingService;

    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          OrderTrackingService orderTrackingService) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderTrackingService = orderTrackingService;
    }

    // Called by OrderService when creating an order
    @Transactional
    public String createRazorpayOrder(BigDecimal amount, Long internalOrderId) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            // Razorpay expects amount in smallest currency unit (paise for INR)
            int amountInPaise = amount.multiply(BigDecimal.valueOf(100)).intValue();

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "order_" + internalOrderId);

            com.razorpay.Order razorpayOrder = client.orders.create(orderRequest);
            String razorpayOrderId = razorpayOrder.get("id");

            log.info("Created Razorpay order {} for internal order {}", razorpayOrderId, internalOrderId);
            return razorpayOrderId;

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage());
            throw new RuntimeException("Payment gateway error: " + e.getMessage());
        }
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
            handlePaymentCaptured(event);
        } else if ("payment.failed".equals(eventType)) {
            handlePaymentFailed(event);
        }
    }

    private void handlePaymentCaptured(JSONObject event) {
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

        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Auto-confirm the order
        Order order = payment.getOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        // Broadcast the status change via WebSocket
        orderTrackingService.broadcastStatusUpdate(order.getId(), OrderStatus.CONFIRMED);

        log.info("Payment captured for order {}", order.getId());
    }

    private void handlePaymentFailed(JSONObject event) {
        JSONObject paymentEntity = event
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String razorpayOrderId = paymentEntity.getString("order_id");

        paymentRepository.findByRazorpayOrderId(razorpayOrderId).ifPresent(payment -> {
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
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes("UTF-8"), "HmacSHA256");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(payload.getBytes("UTF-8"));

            // Convert bytes to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString().equals(signature);

        } catch (Exception e) {
            log.error("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}