package com.bakeaura.payout;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PayoutRequestController {

    private final PayoutRequestService payoutRequestService;

    @PostMapping("/influencer/payout")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<PayoutRequestDto>> submitRequest(
            @Valid @RequestBody PayoutSubmitRequest request,
            Authentication authentication) {
        Long influencerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Payout request submitted",
                payoutRequestService.submitRequest(influencerId, request.getAmount(), request.getUpiId())));
    }

    @GetMapping("/influencer/payout/history")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<List<PayoutRequestDto>>> getHistory(Authentication authentication) {
        Long influencerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Payout history fetched",
                payoutRequestService.getHistoryForInfluencer(influencerId)));
    }

    @GetMapping("/admin/payout/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PayoutRequestDto>>> getPendingRequests() {
        return ResponseEntity.ok(ApiResponse.ok("Pending payouts fetched",
                payoutRequestService.getPendingRequests()));
    }

    @GetMapping("/admin/payout/approved")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PayoutRequestDto>>> getApprovedRequests() {
        return ResponseEntity.ok(ApiResponse.ok("Approved payouts fetched",
                payoutRequestService.getApprovedRequests()));
    }

    @PutMapping("/admin/payout/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PayoutRequestDto>> approveRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Payout approved",
                payoutRequestService.approveRequest(id, adminId)));
    }

    @PutMapping("/admin/payout/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PayoutRequestDto>> rejectRequest(
            @PathVariable Long id,
            @RequestParam String note,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Payout rejected",
                payoutRequestService.rejectRequest(id, adminId, note)));
    }

    @PutMapping("/admin/payout/{id}/mark-paid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PayoutRequestDto>> markAsPaid(
            @PathVariable Long id,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Payout marked as paid",
                payoutRequestService.markAsPaid(id, adminId)));
    }
}
