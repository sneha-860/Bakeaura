package com.bakeaura.influencer;

import com.bakeaura.common.ApiResponse;
import com.bakeaura.enums.CollaborationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/collaborations")
@RequiredArgsConstructor
public class InfluencerCollaborationController {

    private final InfluencerCollaborationService collaborationService;

    @PostMapping("/request/{influencerId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<CollaborationResponse>> requestCollaboration(
            @PathVariable Long influencerId,
            @RequestBody(required = false) CollaborationRequest request,
            Authentication authentication) {

        Long sellerId = Long.parseLong(authentication.getName());
        String message = request != null ? request.getMessage() : null;

        return ResponseEntity.ok(ApiResponse.ok("Collaboration request sent",
                collaborationService.requestCollaboration(sellerId, influencerId, message)));
    }

    @GetMapping("/incoming")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<List<CollaborationResponse>>> getIncomingRequests(
            Authentication authentication) {

        Long influencerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Incoming requests fetched",
                collaborationService.getMyIncomingRequests(influencerId)));
    }

    @GetMapping("/outgoing")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<CollaborationResponse>>> getOutgoingRequests(
            Authentication authentication) {

        Long sellerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Outgoing requests fetched",
                collaborationService.getMyOutgoingRequests(sellerId)));
    }

    @PatchMapping("/respond/{sellerId}")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<CollaborationResponse>> respondToRequest(
            @PathVariable Long sellerId,
            @RequestParam CollaborationStatus status,
            Authentication authentication) {

        Long influencerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Response recorded",
                collaborationService.respondToRequest(influencerId, sellerId, status)));
    }
}
