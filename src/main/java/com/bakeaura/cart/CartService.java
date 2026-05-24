package com.bakeaura.cart;


import com.bakeaura.cart.CartDto;
import com.bakeaura.cart.CartItemDto;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductRepository;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;

    private static final Duration CART_TTL = Duration.ofDays(7);
    private static final String CART_KEY_PREFIX = "cart:";

    private String cartKey(String userEmail) {
        return CART_KEY_PREFIX + userEmail;
    }

    public CartDto getCart(String userEmail) {
        Object cached = redisTemplate.opsForValue().get(cartKey(userEmail));
        if (cached instanceof CartDto cart) {
            return cart;
        }
        return new CartDto(userEmail, new java.util.ArrayList<>());
    }

    public CartDto addItem(String userEmail, Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        CartDto cart = getCart(userEmail);

        // If product already in cart, increment quantity
        Optional<CartItemDto> existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();

        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + quantity);
        } else {
            cart.getItems().add(new CartItemDto(
                    product.getId(),
                    product.getName(),
                    quantity,
                    product.getPrice()
            ));
        }

        saveCart(userEmail, cart);
        return cart;
    }

    public CartDto removeItem(String userEmail, Long productId) {
        CartDto cart = getCart(userEmail);
        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        saveCart(userEmail, cart);
        return cart;
    }

    public CartDto updateQuantity(String userEmail, Long productId, int quantity) {
        CartDto cart = getCart(userEmail);

        if (quantity <= 0) {
            return removeItem(userEmail, productId);
        }

        cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .ifPresent(item -> item.setQuantity(quantity));

        saveCart(userEmail, cart);
        return cart;
    }

    public void clearCart(String userEmail) {
        redisTemplate.delete(cartKey(userEmail));
    }

    private void saveCart(String userEmail, CartDto cart) {
        redisTemplate.opsForValue().set(cartKey(userEmail), cart, CART_TTL);
    }
}
