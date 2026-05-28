package com.bakeaura.favorite;

import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductDto;
import com.bakeaura.product.ProductRepository;
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
    private final ProductRepository productRepository;
    private final ProductService productService;

    public List<ProductDto> getFavorites(String email) {
        User user = getUser(email);
        return favoriteRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(Favorite::getProduct)
                .map(productService::toDto)
                .toList();
    }

    @Transactional
    public List<ProductDto> addFavorite(String email, Long productId) {
        User user = getUser(email);
        Product product = getProduct(productId);
        if (!favoriteRepository.existsByUserAndProduct(user, product)) {
            Favorite favorite = new Favorite();
            favorite.setUser(user);
            favorite.setProduct(product);
            favoriteRepository.save(favorite);
        }
        return getFavorites(email);
    }

    @Transactional
    public List<ProductDto> removeFavorite(String email, Long productId) {
        User user = getUser(email);
        Product product = getProduct(productId);
        favoriteRepository.findByUserAndProduct(user, product).ifPresent(favoriteRepository::delete);
        return getFavorites(email);
    }

    public boolean isFavorite(String email, Long productId) {
        return favoriteRepository.existsByUserAndProduct(getUser(email), getProduct(productId));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }
}
