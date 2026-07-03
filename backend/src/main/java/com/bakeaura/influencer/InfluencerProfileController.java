package com.bakeaura.influencer;

import com.bakeaura.common.ApiResponse;
import com.bakeaura.referral.ReferralCodeResponse;
import com.bakeaura.referral.ReferralCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.bakeaura.referral.InfluencerAnalyticsDto;
import com.bakeaura.referral.ReferralOrderService;

import java.util.List;

@RestController
@RequestMapping("/api/influencer")
@RequiredArgsConstructor
public class InfluencerProfileController {

    private final InfluencerProfileService influencerProfileService;
    private final ReferralCodeService referralCodeService;
    private final ReferralOrderService referralOrderService;

    @GetMapping("/profile")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<InfluencerProfileResponse>> getMyProfile(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Profile fetched", influencerProfileService.getMyProfile(userId)));
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<InfluencerProfileResponse>> updateMyProfile(
            Authentication authentication,
            @RequestBody InfluencerProfileUpdateRequest request) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", influencerProfileService.updateMyProfile(userId, request)));
    }

    @GetMapping("/profile/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InfluencerProfileResponse>> getProfileByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Profile fetched", influencerProfileService.getMyProfile(userId)));
    }

    @GetMapping("/referral-codes")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<List<ReferralCodeResponse>>> getMyReferralCodes(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Referral codes fetched", referralCodeService.getMyActiveCodes(userId)));
    }

    @GetMapping("/dashboard/analytics")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<InfluencerAnalyticsDto>> getDashboardAnalytics(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Analytics fetched", referralOrderService.getDashboardAnalytics(userId)));
    }
}
