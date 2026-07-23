package com.bakeaura.order;

import com.bakeaura.enums.OrderStatus;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.map.MapService;
import com.bakeaura.cart.CartService;
import com.bakeaura.notification.EmailService;
import com.bakeaura.notification.NotificationService;
import com.bakeaura.notification.SmsService;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductService;
import com.bakeaura.referral.ReferralOrderService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import com.bakeaura.websocket.OrderTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductService productService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MapService mapService;

    @Mock
    private OrderTrackingService orderTrackingService;

    @Mock
    private CartService cartService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EmailService emailService;

    @Mock
    private SmsService smsService;

    @Mock
    private ReferralOrderService referralOrderService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                productService,
                userRepository,
                mapService,
                cartService,
                notificationService,
                orderTrackingService,
                eventPublisher,
                emailService,
                smsService,
                referralOrderService
        );
    }

    @Test
    void customerCanCreateOrder() {
        User customer = user(1L, "Customer", "customer@example.com", Role.CUSTOMER);
        User seller = seller();
        Product product = product(10L, seller, 5, true);
        CreateOrderRequestDto request = request(2L, 10L, 2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(mapService.calculateEstimatedRoadDistance(28.0, 77.0, 28.1, 77.1)).thenReturn(5.0);
        when(mapService.isWithinDeliveryRadius(5.0)).thenReturn(true);
        when(productService.getProductEntityById(10L)).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(100L);
            }
            return order;
        });
        doAnswer(invocation -> {
            OrderCreatedEvent event = invocation.getArgument(0);
            event.getOrder().setRazorpayOrderId("order_razorpay");
            return null;
        }).when(eventPublisher).publishEvent(any(OrderCreatedEvent.class));

        OrderResponseDto response = orderService.createOrder(request, 1L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("200");
        assertThat(response.getRazorpayOrderId()).isEqualTo("order_razorpay");
        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void createOrderRejectsTargetUserThatIsNotSeller() {
        User customer = user(1L, "Customer", "customer@example.com", Role.CUSTOMER);
        User notSeller = user(2L, "Other", "other@example.com", Role.CUSTOMER);

        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(notSeller));

        assertThatThrownBy(() -> orderService.createOrder(request(2L, 10L, 1), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Target user is not a seller");
    }

    @Test
    void createOrderRejectsProductFromDifferentSeller() {
        User customer = user(1L, "Customer", "customer@example.com", Role.CUSTOMER);
        User seller = seller();
        User otherSeller = user(3L, "Other Seller", "other-seller@example.com", Role.SELLER);
        Product product = product(10L, otherSeller, 5, true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(mapService.calculateEstimatedRoadDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(5.0);
        when(mapService.isWithinDeliveryRadius(5.0)).thenReturn(true);
        when(productService.getProductEntityById(10L)).thenReturn(product);

        assertThatThrownBy(() -> orderService.createOrder(request(2L, 10L, 1), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Product 10 does not belong to the seller");
    }

    @Test
    void createOrderRejectsOutsideDeliveryRadius() {
        User customer = user(1L, "Customer", "customer@example.com", Role.CUSTOMER);
        User seller = seller();

        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(mapService.calculateEstimatedRoadDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(11.0);
        when(mapService.isWithinDeliveryRadius(11.0)).thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(request(2L, 10L, 1), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Delivery address is outside the seller's delivery radius");
    }

    @Test
    void createOrderRejectsUnavailableProduct() {
        User customer = user(1L, "Customer", "customer@example.com", Role.CUSTOMER);
        User seller = seller();
        Product product = product(10L, seller, 5, false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(mapService.calculateEstimatedRoadDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(5.0);
        when(mapService.isWithinDeliveryRadius(5.0)).thenReturn(true);
        when(productService.getProductEntityById(10L)).thenReturn(product);

        assertThatThrownBy(() -> orderService.createOrder(request(2L, 10L, 1), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Product 10 is not available");
    }

    @Test
    void createOrderRejectsQuantityAboveStock() {
        User customer = user(1L, "Customer", "customer@example.com", Role.CUSTOMER);
        User seller = seller();
        Product product = product(10L, seller, 1, true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(mapService.calculateEstimatedRoadDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(5.0);
        when(mapService.isWithinDeliveryRadius(5.0)).thenReturn(true);
        when(productService.getProductEntityById(10L)).thenReturn(product);

        assertThatThrownBy(() -> orderService.createOrder(request(2L, 10L, 2), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Requested quantity exceeds available stock for product 10");
    }

    @Test
    void updateStatusRejectsInvalidTransition() {
        Order order = order(100L, user(1L, "Customer", "customer@example.com", Role.CUSTOMER), seller(), OrderStatus.PENDING);
        when(orderRepository.findByIdWithItems(100L)).thenReturn(Optional.of(order));
        when(userRepository.findById(2L)).thenReturn(Optional.of(order.getSeller()));

        assertThatThrownBy(() -> orderService.updateStatus(100L, OrderStatus.DELIVERED, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot transition from PENDING to DELIVERED");
    }

    @Test
    void unrelatedSellerCannotUpdateStatus() {
        Order order = order(100L, user(1L, "Customer", "customer@example.com", Role.CUSTOMER), seller(), OrderStatus.PENDING);
        User otherSeller = user(3L, "Other Seller", "other-seller@example.com", Role.SELLER);

        when(orderRepository.findByIdWithItems(100L)).thenReturn(Optional.of(order));
        when(userRepository.findById(3L)).thenReturn(Optional.of(otherSeller));

        assertThatThrownBy(() -> orderService.updateStatus(100L, OrderStatus.CONFIRMED, 3L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You are not authorised to update this order");
    }

    @Test
    void sellerCanUpdateOwnOrderStatus() {
        // PENDING → CONFIRMED is only set by PaymentService after capture, not via updateStatus.
        // Valid seller-driven transition: CONFIRMED → PREPARING.
        Order order = order(100L, user(1L, "Customer", "customer@example.com", Role.CUSTOMER), seller(), OrderStatus.CONFIRMED);
        when(orderRepository.findByIdWithItems(100L)).thenReturn(Optional.of(order));
        when(userRepository.findById(2L)).thenReturn(Optional.of(order.getSeller()));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponseDto response = orderService.updateStatus(100L, OrderStatus.PREPARING, 2L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PREPARING);
        verify(orderTrackingService).broadcastStatusUpdate(100L, OrderStatus.PREPARING);
        verify(notificationService).notifyUser(eq(1L), eq("ORDER_STATUS"), any(), eq(100L));
    }

    @Test
    void getOrderByIdRejectsUnrelatedUser() {
        // Service throws ResourceNotFoundException (404) for unrelated users — not 403 —
        // so as not to reveal order existence to unauthorized parties.
        Order order = order(100L, user(1L, "Customer", "customer@example.com", Role.CUSTOMER), seller(), OrderStatus.PENDING);
        User otherCustomer = user(4L, "Other", "other@example.com", Role.CUSTOMER);

        when(orderRepository.findByIdWithItems(100L)).thenReturn(Optional.of(order));
        when(userRepository.findById(4L)).thenReturn(Optional.of(otherCustomer));

        assertThatThrownBy(() -> orderService.getOrderById(100L, 4L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found");
    }

    private CreateOrderRequestDto request(Long sellerId, Long productId, int quantity) {
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setSellerId(sellerId);
        request.setDeliveryAddress("Delhi");
        request.setDeliveryLatitude(28.1);
        request.setDeliveryLongitude(77.1);

        CreateOrderRequestDto.OrderItemRequest item = new CreateOrderRequestDto.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        request.setItems(List.of(item));
        return request;
    }

    private User seller() {
        User seller = user(2L, "Seller", "seller@example.com", Role.SELLER);
        seller.setLatitude(28.0);
        seller.setLongitude(77.0);
        return seller;
    }

    private User user(Long id, String name, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setRole(role);
        user.setIsEmailVerified(true);
        return user;
    }

    private Product product(Long id, User seller, Integer stock, boolean available) {
        Product product = new Product();
        product.setId(id);
        product.setName("Cake");
        product.setPrice(BigDecimal.valueOf(100));
        product.setSeller(seller);
        product.setStockQuantity(stock);
        product.setIsAvailable(available);
        return product;
    }

    private Order order(Long id, User customer, User seller, OrderStatus status) {
        Order order = Order.builder()
                .id(id)
                .customer(customer)
                .seller(seller)
                .status(status)
                .deliveryAddress("Delhi")
                .deliveryLatitude(28.1)
                .deliveryLongitude(77.1)
                .totalAmount(BigDecimal.valueOf(100))
                .build();
        order.addItem(OrderItem.builder()
                .product(product(10L, seller, 5, true))
                .quantity(1)
                .priceAtPurchase(BigDecimal.valueOf(100))
                .build());
        return order;
    }
}
