package com.bakeaura.influencer;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/influencer")
@RequiredArgsConstructor
public class InfluencerProfileController {

    private final InfluencerProfileService influencerProfileService;

    @GetMapping("/profile")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<InfluencerProfileResponse> getMyProfile(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(influencerProfileService.getMyProfile(userId));
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<InfluencerProfileResponse> updateMyProfile(
            Authentication authentication,
            @RequestBody InfluencerProfileUpdateRequest request) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(influencerProfileService.updateMyProfile(userId, request));
    }

    @GetMapping("/profile/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InfluencerProfileResponse> getProfileByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(influencerProfileService.getMyProfile(userId));
    }
}
