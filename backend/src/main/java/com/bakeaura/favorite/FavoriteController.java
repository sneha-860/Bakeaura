package com.bakeaura.favorite;

import com.bakeaura.common.ApiResponse;
import com.bakeaura.product.ProductDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
public class FavoriteController {
    private final FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductDto>>> getFavorites(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Favorites fetched", favoriteService.getFavorites(authentication.getName())));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<List<ProductDto>>> addFavorite(Authentication authentication, @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Favorite added", favoriteService.addFavorite(authentication.getName(), productId)));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<List<ProductDto>>> removeFavorite(Authentication authentication, @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Favorite removed", favoriteService.removeFavorite(authentication.getName(), productId)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> isFavorite(Authentication authentication, @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Favorite status fetched", Map.of(
                "favorite", favoriteService.isFavorite(authentication.getName(), productId)
        )));
    }
}
