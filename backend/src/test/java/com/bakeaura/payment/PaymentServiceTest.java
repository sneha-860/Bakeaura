package com.bakeaura.payment;

import com.bakeaura.enums.OrderStatus;
import com.bakeaura.enums.PaymentStatus;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.order.Order;
import com.bakeaura.order.OrderItem;
import com.bakeaura.order.OrderService;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import com.bakeaura.cart.CartService;
import com.bakeaura.notification.NotificationService;
import com.razorpay.RazorpayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private ProductService productService;

    @Mock
    private CartService cartService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                razorpayClient,
                paymentRepository,
                productService,
                userRepository,
                notificationService,
                orderService,
                cartService,
                eventPublisher
        );
        ReflectionTestUtils.setField(paymentService, "webhookSecret", "test_secret");
    }

    @Test
    void createPendingPaymentStoresPaymentForOrder() {
        Order order = orderWithProductStock(5);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.createPendingPayment(order, "order_razorpay");

        assertThat(payment.getOrder()).isEqualTo(order);
        assertThat(payment.getRazorpayOrderId()).isEqualTo("order_razorpay");
        assertThat(payment.getAmount()).isEqualByComparingTo("100");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void capturedWebhookReducesStockAndPublishesEvent() {
        Order order = orderWithProductStock(5);
        Payment payment = Payment.builder()
                .order(order)
                .razorpayOrderId("order_razorpay")
                .amount(order.getTotalAmount())
                .status(PaymentStatus.PENDING)
                .build();
        String payload = capturedPayload();

        when(paymentRepository.findWithLockByRazorpayOrderId("order_razorpay")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(payload, signature(payload));

        Product product = order.getItems().get(0).getProduct();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getRazorpayPaymentId()).isEqualTo("pay_123");
        assertThat(payment.getRazorpaySignature()).isEqualTo(signature(payload));
        assertThat(product.getStockQuantity()).isEqualTo(3);
        verify(productService).saveProduct(product);
        verify(paymentRepository).save(payment);
        // Order confirmation is handled by OrderService via the PaymentCapturedEvent, not directly
        verify(eventPublisher).publishEvent(any(PaymentCapturedEvent.class));
    }

    @Test
    void duplicateCapturedWebhookDoesNotReduceStockAgain() {
        Order order = orderWithProductStock(5);
        Payment payment = Payment.builder()
                .order(order)
                .razorpayOrderId("order_razorpay")
                .amount(order.getTotalAmount())
                .status(PaymentStatus.CAPTURED)
                .build();
        String payload = capturedPayload();

        when(paymentRepository.findWithLockByRazorpayOrderId("order_razorpay")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(payload, signature(payload));

        Product product = order.getItems().get(0).getProduct();
        assertThat(product.getStockQuantity()).isEqualTo(5);
        verify(productService, never()).saveProduct(any());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void webhookIdempotencyKeepsPaymentAndOrderStateIntact() {
        Order order = orderWithProductStock(5);
        order.setStatus(OrderStatus.CONFIRMED);
        Payment payment = Payment.builder()
                .order(order)
                .razorpayOrderId("order_razorpay")
                .amount(order.getTotalAmount())
                .status(PaymentStatus.CAPTURED)
                .build();
        String payload = capturedPayload();

        when(paymentRepository.findWithLockByRazorpayOrderId("order_razorpay")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(payload, signature(payload));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getItems().get(0).getProduct().getStockQuantity()).isEqualTo(5);
        verify(paymentRepository, never()).save(any());
        verify(productService, never()).saveProduct(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void invalidWebhookSignatureIsRejected() {
        assertThatThrownBy(() -> paymentService.handleWebhook(capturedPayload(), "bad_signature"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid webhook signature");
    }

    @Test
    void failedWebhookMarksPaymentFailedAndDelegatesCancellationToOrderService() {
        Order order = orderWithProductStock(5);
        Payment payment = Payment.builder()
                .order(order)
                .razorpayOrderId("order_razorpay")
                .amount(order.getTotalAmount())
                .status(PaymentStatus.PENDING)
                .build();
        String payload = failedPayload();

        when(paymentRepository.findByRazorpayOrderId("order_razorpay")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(payload, signature(payload));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getRazorpayPaymentId()).isEqualTo("pay_failed");
        verify(paymentRepository).save(payment);
        // Order cancellation is delegated to OrderService, not done directly
        verify(orderService).markOrderCancelledByPaymentFailure(order);
    }

    @Test
    void failedWebhookDoesNotCancelCapturedPayment() {
        Order order = orderWithProductStock(5);
        order.setStatus(OrderStatus.CONFIRMED);
        Payment payment = Payment.builder()
                .order(order)
                .razorpayOrderId("order_razorpay")
                .amount(order.getTotalAmount())
                .status(PaymentStatus.CAPTURED)
                .build();
        String payload = failedPayload();

        when(paymentRepository.findByRazorpayOrderId("order_razorpay")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(payload, signature(payload));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(paymentRepository, never()).save(any());
        verify(orderService, never()).markOrderCancelledByPaymentFailure(any());
    }

    @Test
    void unknownWebhookEventIsIgnored() {
        String payload = """
                {
                  "event": "refund.created",
                  "payload": {}
                }
                """;

        paymentService.handleWebhook(payload, signature(payload));

        verifyNoInteractions(paymentRepository, orderService, productService, cartService, eventPublisher);
    }

    @Test
    void getPaymentByOrderIdReturnsPaymentForOrderCustomer() {
        User customer = user(1L, "customer@example.com", Role.CUSTOMER);
        User seller = user(2L, "seller@example.com", Role.SELLER);
        Order order = orderWithProductStock(5);
        order.setCustomer(customer);
        order.setSeller(seller);
        Payment payment = Payment.builder()
                .id(20L)
                .order(order)
                .razorpayOrderId("order_razorpay")
                .razorpayPaymentId("pay_123")
                .amount(order.getTotalAmount())
                .status(PaymentStatus.CAPTURED)
                .build();

        when(paymentRepository.findByOrder_Id(1L)).thenReturn(Optional.of(payment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        PaymentResponseDto response = paymentService.getPaymentByOrderId(1L, 1L);

        assertThat(response.getId()).isEqualTo(20L);
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void toPaiseRoundsSafely() {
        assertThat(paymentService.toPaise(BigDecimal.valueOf(123.456))).isEqualTo(12346);
    }

    private Order orderWithProductStock(int stock) {
        Product product = new Product();
        product.setId(10L);
        product.setName("Cake");
        product.setPrice(BigDecimal.valueOf(100));
        product.setStockQuantity(stock);

        Order order = Order.builder()
                .id(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(100))
                .deliveryAddress("Delhi")
                .build();
        order.addItem(OrderItem.builder()
                .product(product)
                .quantity(2)
                .priceAtPurchase(BigDecimal.valueOf(100))
                .build());
        return order;
    }

    private String capturedPayload() {
        return """
                {
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "order_id": "order_razorpay",
                        "id": "pay_123"
                      }
                    }
                  }
                }
                """;
    }

    private String failedPayload() {
        return """
                {
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "order_id": "order_razorpay",
                        "id": "pay_failed"
                      }
                    }
                  }
                }
                """;
    }

    private User user(Long id, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test_secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String value = Integer.toHexString(0xff & b);
                if (value.length() == 1) hex.append('0');
                hex.append(value);
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
