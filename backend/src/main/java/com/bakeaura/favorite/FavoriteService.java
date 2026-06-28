package com.bakeaura.favorite;

import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductDto;
import com.bakeaura.product.ProductService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    public List<ProductDto> getFavorites(Long userId) {
        User user = getUser(userId);
        return favoriteRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(Favorite::getProduct)
                .map(productService::toDto)
                .toList();
    }

    @Transactional
    public List<ProductDto> addFavorite(Long userId, Long productId) {
        User user = getUser(userId);
        Product product = getProduct(productId);
        if (!favoriteRepository.existsByUserAndProduct(user, product)) {
            Favorite favorite = new Favorite();
            favorite.setUser(user);
            favorite.setProduct(product);
            favoriteRepository.save(favorite);
        }
        return getFavorites(userId);
    }

    @Transactional
    public List<ProductDto> removeFavorite(Long userId, Long productId) {
        User user = getUser(userId);
        Product product = getProduct(productId);
        favoriteRepository.findByUserAndProduct(user, product).ifPresent(favoriteRepository::delete);
        return getFavorites(userId);
    }

    public boolean isFavorite(Long userId, Long productId) {
        return favoriteRepository.existsByUserAndProduct(getUser(userId), getProduct(productId));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Product getProduct(Long productId) {
        return productService.getProductEntityById(productId);
    }
}
