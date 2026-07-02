package com.bakeaura.payout;

import com.bakeaura.common.ApiResponse;
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
public class PayoutRequestController {

    private final PayoutRequestService payoutRequestService;

    @PostMapping("/influencer/payout")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<PayoutRequest>> submitRequest(
            @RequestParam BigDecimal amount,
            @RequestParam String upiId,
            Authentication authentication) {
        Long influencerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Payout request submitted",
                payoutRequestService.submitRequest(influencerId, amount, upiId)));
    }

    @GetMapping("/influencer/payout/history")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<List<PayoutRequest>>> getHistory(Authentication authentication) {
        Long influencerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Payout history fetched",
                payoutRequestService.getHistoryForInfluencer(influencerId)));
    }

    @GetMapping("/admin/payout/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PayoutRequest>>> getPendingRequests() {
        return ResponseEntity.ok(ApiResponse.ok("Pending payouts fetched",
                payoutRequestService.getPendingRequests()));
    }

    @GetMapping("/admin/payout/approved")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PayoutRequest>>> getApprovedRequests() {
        return ResponseEntity.ok(ApiResponse.ok("Approved payouts fetched",
                payoutRequestService.getApprovedRequests()));
    }

    @PutMapping("/admin/payout/{id}/mark-paid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PayoutRequest>> markAsPaid(
            @PathVariable Long id,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Payout marked as paid",
                payoutRequestService.markAsPaid(id, adminId)));
    }

    @PutMapping("/admin/payout/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PayoutRequest>> approveRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Payout approved",
                payoutRequestService.approveRequest(id, adminId)));
    }

    @PutMapping("/admin/payout/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PayoutRequest>> rejectRequest(
            @PathVariable Long id,
            @RequestParam String note,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Payout rejected",
                payoutRequestService.rejectRequest(id, adminId, note)));
    }
}
