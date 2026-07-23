package com.bakeaura.seller;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sellers")
@RequiredArgsConstructor
@Validated
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
            @RequestParam(defaultValue = "10.0")
            @DecimalMin(value = "0.5", message = "Radius must be at least 0.5 km")
            @DecimalMax(value = "100.0", message = "Radius cannot exceed 100 km")
            double radius) {
        return ResponseEntity.ok(ApiResponse.ok("Nearby sellers fetched",
                sellerService.getNearbySellers(latitude, longitude, radius)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SellerProfileDto>> getSeller(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Seller fetched", sellerService.getSeller(id)));
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<SellerProfileDto>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateSellerProfileDto request) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Profile updated",
                sellerService.updateProfile(userId, request)));
    }

    @PatchMapping("/toggle-open")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<SellerProfileDto>> toggleOpen(
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Shop status toggled",
                sellerService.toggleOpen(userId)));
    }

    @GetMapping("/dashboard/analytics")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<SellerAnalyticsDto>> getDashboardAnalytics(
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Analytics fetched",
                sellerService.getDashboardAnalytics(userId)));
    }
}
