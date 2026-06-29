package com.bakeaura.payout;

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
    public ResponseEntity<PayoutRequest> submitRequest(
            @RequestParam BigDecimal amount,
            @RequestParam String upiId,
            Authentication authentication) {
        Long influencerId = Long.parseLong(authentication.getName());
        PayoutRequest result = payoutRequestService.submitRequest(influencerId, amount, upiId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/influencer/payout/history")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<List<PayoutRequest>> getHistory(Authentication authentication) {
        Long influencerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(payoutRequestService.getHistoryForInfluencer(influencerId));
    }

    @GetMapping("/admin/payout/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PayoutRequest>> getPendingRequests() {
        return ResponseEntity.ok(payoutRequestService.getPendingRequests());
    }

    @GetMapping("/admin/payout/approved")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PayoutRequest>> getApprovedRequests() {
        return ResponseEntity.ok(payoutRequestService.getApprovedRequests());
    }

    @PutMapping("/admin/payout/{id}/mark-paid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutRequest> markAsPaid(
            @PathVariable Long id,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(payoutRequestService.markAsPaid(id, adminId));
    }

    @PutMapping("/admin/payout/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutRequest> approveRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(payoutRequestService.approveRequest(id, adminId));
    }

    @PutMapping("/admin/payout/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutRequest> rejectRequest(
            @PathVariable Long id,
            @RequestParam String note,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(payoutRequestService.rejectRequest(id, adminId, note));
    }
}
