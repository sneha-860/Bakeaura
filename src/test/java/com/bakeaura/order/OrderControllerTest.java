package com.bakeaura.order;

import com.bakeaura.auth.JwtUtil;
import com.bakeaura.enums.OrderStatus;
import com.bakeaura.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, OrderControllerTest.MethodSecurityTestConfig.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

            return http.build();
        }
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void customerCanCreateOrder() throws Exception {
        when(orderService.createOrder(any(CreateOrderRequestDto.class), eq("customer@example.com")))
                .thenReturn(response(OrderStatus.PENDING));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOrderJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order created"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(orderService).createOrder(any(CreateOrderRequestDto.class), eq("customer@example.com"));
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void sellerCannotCreateOrder() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOrderJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(orderService);
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void createOrderRejectsInvalidQuantity() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sellerId": 2,
                                  "deliveryAddress": "Delhi",
                                  "deliveryLatitude": 28.1,
                                  "deliveryLongitude": 77.1,
                                  "items": [
                                    {
                                      "productId": 10,
                                      "quantity": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(orderService);
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void createOrderRejectsInvalidCoordinates() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sellerId": 2,
                                  "deliveryAddress": "Delhi",
                                  "deliveryLatitude": 91,
                                  "deliveryLongitude": 77.1,
                                  "items": [
                                    {
                                      "productId": 10,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(orderService);
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void sellerCanUpdateStatus() throws Exception {
        when(orderService.updateStatus(1L, OrderStatus.PREPARING, "seller@example.com"))
                .thenReturn(response(OrderStatus.PREPARING));

        mockMvc.perform(patch("/api/orders/1/status").param("status", "PREPARING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order status updated"))
                .andExpect(jsonPath("$.data.status").value("PREPARING"));

        verify(orderService).updateStatus(1L, OrderStatus.PREPARING, "seller@example.com");
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void customerCanGetOwnOrders() throws Exception {
        when(orderService.getMyOrders("customer@example.com"))
                .thenReturn(List.of(response(OrderStatus.PENDING)));

        mockMvc.perform(get("/api/orders/my-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        verify(orderService).getMyOrders("customer@example.com");
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void sellerCanGetSellerOrders() throws Exception {
        when(orderService.getSellerOrders("seller@example.com", null))
                .thenReturn(List.of(response(OrderStatus.CONFIRMED)));

        mockMvc.perform(get("/api/orders/seller-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"));

        verify(orderService).getSellerOrders("seller@example.com", null);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void authenticatedUserCanGetOrderById() throws Exception {
        when(orderService.getOrderById(1L, "admin@example.com"))
                .thenReturn(response(OrderStatus.CONFIRMED));

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order fetched"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        verify(orderService).getOrderById(1L, "admin@example.com");
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotGetOrderById() throws Exception {
        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(orderService);
    }

    private String validOrderJson() {
        return """
                {
                  "sellerId": 2,
                  "deliveryAddress": "Delhi",
                  "deliveryLatitude": 28.1,
                  "deliveryLongitude": 77.1,
                  "items": [
                    {
                      "productId": 10,
                      "quantity": 1
                    }
                  ]
                }
                """;
    }

    private OrderResponseDto response(OrderStatus status) {
        return OrderResponseDto.builder()
                .id(1L)
                .customerName("Customer")
                .sellerName("Seller")
                .status(status)
                .totalAmount(BigDecimal.valueOf(100))
                .deliveryAddress("Delhi")
                .razorpayOrderId("order_razorpay")
                .items(List.of())
                .build();
    }
}
