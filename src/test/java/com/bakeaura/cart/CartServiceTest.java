package com.bakeaura.cart;

import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductRepository;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(redisTemplate, productRepository, userRepository);
    }

    @Test
    void getCartReturnsEmptyCartWhenRedisHasNoCart() {
        givenUser();
        givenRedisValueOperations();
        when(valueOperations.get("cart:10")).thenReturn(null);

        CartDto cart = cartService.getCart("customer@example.com");

        assertThat(cart.getUserEmail()).isEqualTo("customer@example.com");
        assertThat(cart.getItems()).isEmpty();
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void addItemAddsNewProductToEmptyCart() {
        givenUser();
        givenRedisValueOperations();
        Product product = product(1L, "Cake", "100.00", 5, true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(valueOperations.get("cart:10")).thenReturn(null);

        CartDto cart = cartService.addItem("customer@example.com", 1L, 2);

        assertThat(cart.getItems()).hasSize(1);
        CartItemDto item = cart.getItems().get(0);
        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getProductName()).isEqualTo("Cake");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(cart.getTotalAmount()).isEqualByComparingTo("200.00");
        verify(valueOperations).set(eq("cart:10"), eq(cart), eq(Duration.ofDays(7)));
    }

    @Test
    void addItemIncreasesQuantityWhenProductAlreadyExists() {
        givenUser();
        givenRedisValueOperations();
        Product product = product(1L, "Cake", "100.00", 5, true);
        CartDto existingCart = cartWithItem(1L, "Old Cake", 2, "90.00");

        when(valueOperations.get("cart:10")).thenReturn(existingCart);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        CartDto cart = cartService.addItem("customer@example.com", 1L, 3);

        assertThat(cart.getItems()).hasSize(1);
        CartItemDto item = cart.getItems().get(0);
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.getProductName()).isEqualTo("Cake");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void addItemRejectsUnavailableProduct() {
        givenUser();
        Product product = product(1L, "Cake", "100.00", 5, false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addItem("customer@example.com", 1L, 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Product is not available");

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void addItemRejectsQuantityGreaterThanStock() {
        givenUser();
        givenRedisValueOperations();
        Product product = product(1L, "Cake", "100.00", 2, true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(valueOperations.get("cart:10")).thenReturn(null);

        assertThatThrownBy(() -> cartService.addItem("customer@example.com", 1L, 3))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Requested quantity exceeds available stock");

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void updateQuantityToZeroRemovesItem() {
        givenUser();
        givenRedisValueOperations();
        CartDto existingCart = cartWithItem(1L, "Cake", 2, "100.00");
        when(valueOperations.get("cart:10")).thenReturn(existingCart);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "Cake", "100.00", 5, true)));

        CartDto cart = cartService.updateQuantity("customer@example.com", 1L, 0);

        assertThat(cart.getItems()).isEmpty();
        verify(valueOperations, atLeastOnce()).set(eq("cart:10"), eq(cart), eq(Duration.ofDays(7)));
    }

    @Test
    void updateQuantityRejectsMissingCartItem() {
        givenUser();
        givenRedisValueOperations();
        when(valueOperations.get("cart:10")).thenReturn(new CartDto("customer@example.com", new ArrayList<>()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "Cake", "100.00", 5, true)));

        assertThatThrownBy(() -> cartService.updateQuantity("customer@example.com", 1L, 2))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cart item not found");
    }

    @Test
    void getCartSyncsStaleProductNamePriceAndQuantity() {
        givenUser();
        givenRedisValueOperations();
        CartDto existingCart = cartWithItem(1L, "Old Name", 10, "50.00");
        Product product = product(1L, "New Name", "75.00", 4, true);

        when(valueOperations.get("cart:10")).thenReturn(existingCart);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        CartDto cart = cartService.getCart("customer@example.com");

        CartItemDto item = cart.getItems().get(0);
        assertThat(item.getProductName()).isEqualTo("New Name");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("75.00");
        assertThat(item.getQuantity()).isEqualTo(4);
        verify(valueOperations).set(eq("cart:10"), eq(cart), eq(Duration.ofDays(7)));
    }

    @Test
    void getCartRemovesDeletedUnavailableAndOutOfStockProducts() {
        givenUser();
        givenRedisValueOperations();
        CartDto existingCart = new CartDto("customer@example.com", new ArrayList<>(List.of(
                new CartItemDto(1L, "Deleted", 1, new BigDecimal("10.00")),
                new CartItemDto(2L, "Unavailable", 1, new BigDecimal("20.00")),
                new CartItemDto(3L, "Out", 1, new BigDecimal("30.00")),
                new CartItemDto(4L, "Kept", 1, new BigDecimal("40.00"))
        )));

        when(valueOperations.get("cart:10")).thenReturn(existingCart);
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        when(productRepository.findById(2L)).thenReturn(Optional.of(product(2L, "Unavailable", "20.00", 5, false)));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product(3L, "Out", "30.00", 0, true)));
        when(productRepository.findById(4L)).thenReturn(Optional.of(product(4L, "Kept", "40.00", 5, true)));

        CartDto cart = cartService.getCart("customer@example.com");

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getProductId()).isEqualTo(4L);
    }

    @Test
    void clearCartDeletesRedisKey() {
        givenUser();

        cartService.clearCart("customer@example.com");

        verify(redisTemplate).delete("cart:10");
    }

    @Test
    void removeItemRemovesProductFromCart() {
        givenUser();
        givenRedisValueOperations();
        CartDto existingCart = new CartDto("customer@example.com", new ArrayList<>(List.of(
                new CartItemDto(1L, "Cake", 1, new BigDecimal("100.00")),
                new CartItemDto(2L, "Cookie", 2, new BigDecimal("50.00"))
        )));

        when(valueOperations.get("cart:10")).thenReturn(existingCart);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "Cake", "100.00", 5, true)));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product(2L, "Cookie", "50.00", 5, true)));

        CartDto cart = cartService.removeItem("customer@example.com", 1L);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getProductId()).isEqualTo(2L);
    }

    private void givenUser() {
        User user = new User();
        user.setId(10L);
        user.setEmail("customer@example.com");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
    }

    private void givenRedisValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Product product(Long id, String name, String price, Integer stockQuantity, Boolean isAvailable) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stockQuantity);
        product.setIsAvailable(isAvailable);
        return product;
    }

    private CartDto cartWithItem(Long productId, String productName, int quantity, String unitPrice) {
        return new CartDto("customer@example.com", new ArrayList<>(List.of(
                new CartItemDto(productId, productName, quantity, new BigDecimal(unitPrice))
        )));
    }
}
