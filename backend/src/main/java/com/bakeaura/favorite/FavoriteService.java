package com.bakeaura.favorite;

import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.enums.Role;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductDto;
import com.bakeaura.product.ProductService;
import com.bakeaura.seller.SellerProfileDto;
import com.bakeaura.seller.SellerService;
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
    private final SellerService sellerService;

    // ── Product Favorites ──────────────────────────────────────────────────────

    public List<ProductDto> getFavoriteProducts(Long userId) {
        User user = getUser(userId);
        return favoriteRepository.findByUserAndProductIsNotNullOrderByCreatedAtDesc(user).stream()
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
        return getFavoriteProducts(userId);
    }

    @Transactional
    public List<ProductDto> removeFavorite(Long userId, Long productId) {
        User user = getUser(userId);
        Product product = getProduct(productId);
        favoriteRepository.findByUserAndProduct(user, product).ifPresent(favoriteRepository::delete);
        return getFavoriteProducts(userId);
    }

    public boolean isFavorite(Long userId, Long productId) {
        return favoriteRepository.existsByUserAndProduct(getUser(userId), getProduct(productId));
    }

    // ── Seller Favorites ───────────────────────────────────────────────────────

    public List<SellerProfileDto> getFavoriteSellers(Long userId) {
        User user = getUser(userId);
        return favoriteRepository.findByUserAndSellerIsNotNullOrderByCreatedAtDesc(user).stream()
                .map(Favorite::getSeller)
                .map(s -> sellerService.getSeller(s.getId()))
                .toList();
    }

    @Transactional
    public List<SellerProfileDto> addSellerFavorite(Long userId, Long sellerId) {
        User user = getUser(userId);
        User seller = getSeller(sellerId);
        if (!favoriteRepository.existsByUserAndSeller(user, seller)) {
            Favorite favorite = new Favorite();
            favorite.setUser(user);
            favorite.setSeller(seller);
            favoriteRepository.save(favorite);
        }
        return getFavoriteSellers(userId);
    }

    @Transactional
    public List<SellerProfileDto> removeSellerFavorite(Long userId, Long sellerId) {
        User user = getUser(userId);
        User seller = getSeller(sellerId);
        favoriteRepository.findByUserAndSeller(user, seller).ifPresent(favoriteRepository::delete);
        return getFavoriteSellers(userId);
    }

    public boolean isSellerFavorite(Long userId, Long sellerId) {
        return favoriteRepository.existsByUserAndSeller(getUser(userId), getSeller(sellerId));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Product getProduct(Long productId) {
        return productService.getProductEntityById(productId);
    }

    private User getSeller(Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        if (seller.getRole() != Role.SELLER) {
            throw new BadRequestException("Target user is not a seller");
        }
        return seller;
    }
}
