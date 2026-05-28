package com.bakeaura.cart;


import com.bakeaura.exception.BadRequestException;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductRepository;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    private static final Duration CART_TTL = Duration.ofDays(7);
    private static final String CART_KEY_PREFIX = "cart:";

    private String cartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    public CartDto getCart(String userEmail) {
        User user = getUserByEmail(userEmail);

        return getCartForUser(user);
    }

    public CartDto getCartWithoutSync(String userEmail) {
        User user = getUserByEmail(userEmail);
        Object cached = redisTemplate.opsForValue().get(cartKey(user.getId()));
        if (cached instanceof CartDto cart) {
            return cart;
        }
        return new CartDto(user.getEmail(), new java.util.ArrayList<>());
    }

    private CartDto getCartForUser(User user) {
        Object cached = redisTemplate.opsForValue().get(cartKey(user.getId()));
        if (cached instanceof CartDto cart) {
            CartDto syncedCart = syncCartWithProducts(user.getEmail(), cart);
            saveCart(user, syncedCart);
            return syncedCart;
        }
        return new CartDto(user.getEmail(), new java.util.ArrayList<>());
    }

    public CartDto addItem(String userEmail, Long productId, int quantity) {
        User user = getUserByEmail(userEmail);
        validateQuantity(quantity);

        Product product = getAvailableProduct(productId);

        CartDto cart = getCartForUser(user);

        // If product already in cart, increment quantity
        Optional<CartItemDto> existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();

        if (existing.isPresent()) {
            int newQuantity = existing.get().getQuantity() + quantity;
            validateStock(product, newQuantity);
            existing.get().setProductName(product.getName());
            existing.get().setQuantity(newQuantity);
            existing.get().setUnitPrice(product.getPrice());
        } else {
            validateStock(product, quantity);
            cart.getItems().add(new CartItemDto(
                    product.getId(),
                    product.getName(),
                    quantity,
                    product.getPrice()
            ));
        }

        saveCart(user, cart);
        return cart;
    }

    public CartDto removeItem(String userEmail, Long productId) {
        User user = getUserByEmail(userEmail);
        CartDto cart = getCartForUser(user);
        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        saveCart(user, cart);
        return cart;
    }

    public CartDto updateQuantity(String userEmail, Long productId, int quantity) {
        User user = getUserByEmail(userEmail);
        CartDto cart = getCartForUser(user);

        if (quantity <= 0) {
            return removeItem(userEmail, productId);
        }

        Product product = getAvailableProduct(productId);
        validateStock(product, quantity);

        CartItemDto item = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());

        saveCart(user, cart);
        return cart;
    }

    public void clearCart(String userEmail) {
        User user = getUserByEmail(userEmail);
        redisTemplate.delete(cartKey(user.getId()));
    }

    private void saveCart(User user, CartDto cart) {
        cart.setUserEmail(user.getEmail());
        redisTemplate.opsForValue().set(cartKey(user.getId()), cart, CART_TTL);
    }

    private User getUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Product getAvailableProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (!Boolean.TRUE.equals(product.getIsAvailable())) {
            throw new BadRequestException("Product is not available");
        }

        if (product.getStockQuantity() != null && product.getStockQuantity() <= 0) {
            throw new BadRequestException("Product is out of stock");
        }

        return product;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("Quantity must be at least 1");
        }
    }

    private void validateStock(Product product, int quantity) {
        if (product.getStockQuantity() != null && quantity > product.getStockQuantity()) {
            throw new BadRequestException("Requested quantity exceeds available stock");
        }
    }

    private CartDto syncCartWithProducts(String userEmail, CartDto cart) {
        cart.setUserEmail(userEmail);
        Iterator<CartItemDto> iterator = cart.getItems().iterator();

        while (iterator.hasNext()) {
            CartItemDto item = iterator.next();
            Optional<Product> productOptional = productRepository.findById(item.getProductId());

            if (productOptional.isEmpty()) {
                iterator.remove();
                continue;
            }

            Product product = productOptional.get();
            if (!Boolean.TRUE.equals(product.getIsAvailable())
                    || item.getQuantity() == null
                    || item.getQuantity() <= 0
                    || (product.getStockQuantity() != null && product.getStockQuantity() <= 0)) {
                iterator.remove();
                continue;
            }

            if (product.getStockQuantity() != null && item.getQuantity() > product.getStockQuantity()) {
                item.setQuantity(product.getStockQuantity());
            }

            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
        }

        return cart;
    }
}
