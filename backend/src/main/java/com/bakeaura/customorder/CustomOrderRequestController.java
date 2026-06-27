package com.bakeaura.customorder;

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
    public ResponseEntity<CustomOrderRequest> submitRequest(
            @RequestParam Long sellerId,
            @RequestParam String designBrief,
            @RequestParam String occasion,
            @RequestParam Integer serves,
            @RequestParam BigDecimal budgetMin,
            @RequestParam BigDecimal budgetMax,
            Authentication authentication) {
        Long customerId = Long.parseLong(authentication.getName());
        CustomOrderRequest result = customOrderRequestService.submitRequest(
                customerId, sellerId, designBrief, occasion, serves, budgetMin, budgetMax);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/custom-orders/my-requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<CustomOrderRequest>> getMyRequests(
            Authentication authentication) {
        Long customerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                customOrderRequestService.getRequestsForCustomer(customerId));
    }

    @GetMapping("/seller/custom-orders")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<List<CustomOrderRequest>> getAllRequestsForSeller(
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                customOrderRequestService.getAllRequestsForSeller(sellerId));
    }

    @GetMapping("/seller/custom-orders/pending")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<List<CustomOrderRequest>> getPendingRequestsForSeller(
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                customOrderRequestService.getPendingRequestsForSeller(sellerId));
    }

    @PutMapping("/seller/custom-orders/{id}/accept")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<CustomOrderRequest> acceptRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                customOrderRequestService.acceptRequest(id, sellerId));
    }

    @PutMapping("/seller/custom-orders/{id}/reject")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<CustomOrderRequest> rejectRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                customOrderRequestService.rejectRequest(id, sellerId));
    }

    @PutMapping("/seller/custom-orders/{id}/quote")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<CustomOrderRequest> sendQuote(
            @PathVariable Long id,
            @RequestParam BigDecimal quote,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                customOrderRequestService.sendQuote(id, sellerId, quote));
    }
}
