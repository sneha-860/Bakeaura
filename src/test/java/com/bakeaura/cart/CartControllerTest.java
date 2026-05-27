package com.bakeaura.cart;

import com.bakeaura.auth.JwtUtil;
import com.bakeaura.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, CartControllerTest.MethodSecurityTestConfig.class})
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

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
    void customerCanGetCart() throws Exception {
        CartDto cart = new CartDto("customer@example.com", List.of(
                new CartItemDto(1L, "Cake", 2, new BigDecimal("100.00"))
        ));
        when(cartService.getCart("customer@example.com")).thenReturn(cart);

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cart fetched"))
                .andExpect(jsonPath("$.data.userEmail").value("customer@example.com"))
                .andExpect(jsonPath("$.data.items[0].productId").value(1))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(200.00))
                .andExpect(jsonPath("$.data.totalAmount").value(200.00));

        verify(cartService).getCart("customer@example.com");
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void sellerCannotAccessCart() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCannotAccessCart() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(cartService);
    }

    @Test
    @WithAnonymousUser
    void anonymousUserCannotAccessCart() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void addItemPassesAuthenticatedEmailProductIdAndQuantityToService() throws Exception {
        CartDto cart = new CartDto("customer@example.com", List.of(
                new CartItemDto(5L, "Cookie", 3, new BigDecimal("50.00"))
        ));
        when(cartService.addItem("customer@example.com", 5L, 3)).thenReturn(cart);

        mockMvc.perform(post("/api/cart/items/5").param("quantity", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Item added to cart"))
                .andExpect(jsonPath("$.data.items[0].productId").value(5))
                .andExpect(jsonPath("$.data.totalAmount").value(150.00));

        verify(cartService).addItem("customer@example.com", 5L, 3);
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void addItemRejectsQuantityLessThanOne() throws Exception {
        mockMvc.perform(post("/api/cart/items/5").param("quantity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void updateQuantityRejectsNegativeQuantity() throws Exception {
        mockMvc.perform(patch("/api/cart/items/5").param("quantity", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void clearCartPassesAuthenticatedEmailToService() throws Exception {
        mockMvc.perform(delete("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cart cleared"));

        verify(cartService).clearCart("customer@example.com");
    }
}
