package com.bakeaura.seller;

import com.bakeaura.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SellerProfileDto>>> getSellers() {
        return ResponseEntity.ok(ApiResponse.ok("Sellers fetched", sellerService.getSellers()));
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<SellerProfileDto>>> getNearbySellers(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10.0") double radius) {
        return ResponseEntity.ok(ApiResponse.ok("Nearby sellers fetched",
                sellerService.getNearbySellers(latitude, longitude, radius)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SellerProfileDto>> getSeller(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Seller fetched", sellerService.getSeller(id)));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<SellerProfileDto>> updateProfile(
            Authentication authentication,
            @RequestBody UpdateSellerProfileDto request) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Profile updated",
                sellerService.updateProfile(userId, request)));
    }

    @PatchMapping("/toggle-open")
    public ResponseEntity<ApiResponse<SellerProfileDto>> toggleOpen(
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Shop status toggled",
                sellerService.toggleOpen(userId)));
    }
}
