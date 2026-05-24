package com.bakeaura.cart;


import com.bakeaura.cart.CartDto;
import com.bakeaura.cart.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartDto> getCart(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(cartService.getCart(userDetails.getUsername()));
    }

    @PostMapping("/items/{productId}")
    public ResponseEntity<CartDto> addItem(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int quantity,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(cartService.addItem(userDetails.getUsername(), productId, quantity));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartDto> removeItem(
            @PathVariable Long productId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(cartService.removeItem(userDetails.getUsername(), productId));
    }

    @PatchMapping("/items/{productId}")
    public ResponseEntity<CartDto> updateQuantity(
            @PathVariable Long productId,
            @RequestParam int quantity,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(cartService.updateQuantity(userDetails.getUsername(), productId, quantity));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal UserDetails userDetails) {
        cartService.clearCart(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}