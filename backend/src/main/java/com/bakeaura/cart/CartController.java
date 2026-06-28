package com.bakeaura.cart;


import com.bakeaura.common.ApiResponse;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Validated
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartDto>> getCart(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Cart fetched",
                cartService.getCart(Long.parseLong(authentication.getName()))
        ));
    }

    @PostMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartDto>> addItem(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") @Min(1) int quantity,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Item added to cart",
                cartService.addItem(Long.parseLong(authentication.getName()), productId, quantity)
        ));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartDto>> removeItem(
            @PathVariable Long productId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Item removed from cart",
                cartService.removeItem(Long.parseLong(authentication.getName()), productId)
        ));
    }

    @PatchMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartDto>> updateQuantity(
            @PathVariable Long productId,
            @RequestParam @Min(0) int quantity,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Cart item updated",
                cartService.updateQuantity(Long.parseLong(authentication.getName()), productId, quantity)
        ));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(Authentication authentication) {
        cartService.clearCart(Long.parseLong(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.ok("Cart cleared", null));
    }
}
