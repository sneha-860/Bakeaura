package com.bakeaura.customorder;

import com.bakeaura.common.ApiResponse;
import com.bakeaura.enums.CustomOrderStatus;
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
    public ResponseEntity<ApiResponse<CustomOrderRequest>> submitRequest(
            @RequestBody SubmitCustomOrderDto dto,
            Authentication authentication) {
        Long customerId = Long.parseLong(authentication.getName());
        CustomOrderRequest result = customOrderRequestService.submitRequest(
                customerId, dto.getSellerId(), dto.getDesignBrief(), dto.getOccasion(),
                dto.getServes(), dto.getBudgetMin(), dto.getBudgetMax());
        return ResponseEntity.ok(ApiResponse.ok("Custom order submitted", result));
    }

    @GetMapping("/custom-orders/my-requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<CustomOrderRequest>>> getMyRequests(
            Authentication authentication) {
        Long customerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Custom orders fetched",
                customOrderRequestService.getRequestsForCustomer(customerId)));
    }

    @GetMapping("/seller/custom-orders")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<CustomOrderRequest>>> getAllRequestsForSeller(
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Custom orders fetched",
                customOrderRequestService.getAllRequestsForSeller(sellerId)));
    }

    @GetMapping("/seller/custom-orders/pending")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<CustomOrderRequest>>> getPendingRequestsForSeller(
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Pending custom orders fetched",
                customOrderRequestService.getPendingRequestsForSeller(sellerId)));
    }

    @PutMapping("/seller/custom-orders/{id}/accept")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<CustomOrderRequest>> acceptRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Custom order accepted",
                customOrderRequestService.acceptRequest(id, sellerId)));
    }

    @PutMapping("/seller/custom-orders/{id}/reject")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<CustomOrderRequest>> rejectRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Custom order rejected",
                customOrderRequestService.rejectRequest(id, sellerId)));
    }

    @PutMapping("/seller/custom-orders/{id}/quote")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<CustomOrderRequest>> sendQuote(
            @PathVariable Long id,
            @RequestParam BigDecimal quote,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Quote sent",
                customOrderRequestService.sendQuote(id, sellerId, quote)));
    }
}
