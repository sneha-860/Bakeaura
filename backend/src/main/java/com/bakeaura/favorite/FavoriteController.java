package com.bakeaura.favorite;

import com.bakeaura.common.ApiResponse;
import com.bakeaura.product.ProductDto;
import com.bakeaura.seller.SellerProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class FavoriteController {
    private final FavoriteService favoriteService;

    // ── Product Favorites ──────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductDto>>> getFavoriteProducts(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Favorites fetched",
                favoriteService.getFavoriteProducts(Long.parseLong(authentication.getName()))));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<List<ProductDto>>> addFavorite(Authentication authentication,
                                                                     @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Favorite added",
                favoriteService.addFavorite(Long.parseLong(authentication.getName()), productId)));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<List<ProductDto>>> removeFavorite(Authentication authentication,
                                                                        @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Favorite removed",
                favoriteService.removeFavorite(Long.parseLong(authentication.getName()), productId)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> isFavorite(Authentication authentication,
                                                                        @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Favorite status fetched", Map.of(
                "favorite", favoriteService.isFavorite(Long.parseLong(authentication.getName()), productId)
        )));
    }

    // ── Seller Favorites ───────────────────────────────────────────────────────

    @GetMapping("/sellers")
    public ResponseEntity<ApiResponse<List<SellerProfileDto>>> getFavoriteSellers(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Favourite sellers fetched",
                favoriteService.getFavoriteSellers(Long.parseLong(authentication.getName()))));
    }

    @PostMapping("/sellers/{sellerId}")
    public ResponseEntity<ApiResponse<List<SellerProfileDto>>> addSellerFavorite(Authentication authentication,
                                                                                  @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok("Seller added to favourites",
                favoriteService.addSellerFavorite(Long.parseLong(authentication.getName()), sellerId)));
    }

    @DeleteMapping("/sellers/{sellerId}")
    public ResponseEntity<ApiResponse<List<SellerProfileDto>>> removeSellerFavorite(Authentication authentication,
                                                                                     @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok("Seller removed from favourites",
                favoriteService.removeSellerFavorite(Long.parseLong(authentication.getName()), sellerId)));
    }

    @GetMapping("/sellers/{sellerId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> isSellerFavorite(Authentication authentication,
                                                                               @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok("Favourite status fetched", Map.of(
                "favorite", favoriteService.isSellerFavorite(Long.parseLong(authentication.getName()), sellerId)
        )));
    }
}
