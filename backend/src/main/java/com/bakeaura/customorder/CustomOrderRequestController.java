package com.bakeaura.customorder;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CustomOrderRequestController {

    private final CustomOrderRequestService customOrderRequestService;

    @PostMapping("/custom-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CustomOrderResponseDto>> submitRequest(
            @Valid @RequestBody SubmitCustomOrderDto dto,
            Authentication authentication) {
        Long customerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Custom order submitted",
                customOrderRequestService.submitRequest(
                        customerId, dto.getSellerId(), dto.getDesignBrief(), dto.getOccasion(),
                        dto.getServes(), dto.getBudgetMin(), dto.getBudgetMax())));
    }

    @GetMapping("/custom-orders/my-requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<CustomOrderResponseDto>>> getMyRequests(
            Authentication authentication) {
        Long customerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Custom orders fetched",
                customOrderRequestService.getRequestsForCustomer(customerId)));
    }

    @GetMapping("/seller/custom-orders")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<CustomOrderResponseDto>>> getAllRequestsForSeller(
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Custom orders fetched",
                customOrderRequestService.getAllRequestsForSeller(sellerId)));
    }

    @GetMapping("/seller/custom-orders/pending")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<CustomOrderResponseDto>>> getPendingRequestsForSeller(
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Pending custom orders fetched",
                customOrderRequestService.getPendingRequestsForSeller(sellerId)));
    }

    @PutMapping("/seller/custom-orders/{id}/accept")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<CustomOrderResponseDto>> acceptRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Custom order accepted",
                customOrderRequestService.acceptRequest(id, sellerId)));
    }

    @PutMapping("/seller/custom-orders/{id}/reject")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<CustomOrderResponseDto>> rejectRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Custom order rejected",
                customOrderRequestService.rejectRequest(id, sellerId)));
    }

    @PutMapping("/seller/custom-orders/{id}/quote")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<CustomOrderResponseDto>> sendQuote(
            @PathVariable Long id,
            @RequestParam BigDecimal quote,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Quote sent",
                customOrderRequestService.sendQuote(id, sellerId, quote)));
    }
}
