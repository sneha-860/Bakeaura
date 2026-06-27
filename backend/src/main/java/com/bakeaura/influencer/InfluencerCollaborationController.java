package com.bakeaura.influencer;

import com.bakeaura.enums.CollaborationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/collaborations")
@RequiredArgsConstructor
public class InfluencerCollaborationController {

    private final InfluencerCollaborationService collaborationService;

    @PostMapping("/request/{influencerId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<CollaborationResponse> requestCollaboration(
            @PathVariable Long influencerId,
            @RequestBody(required = false) CollaborationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long sellerId = Long.parseLong(userDetails.getUsername());
        String message = request != null ? request.getMessage() : null;

        return ResponseEntity.ok(
                collaborationService.requestCollaboration(sellerId, influencerId, message));
    }

    @GetMapping("/incoming")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<List<CollaborationResponse>> getIncomingRequests(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long influencerId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(collaborationService.getMyIncomingRequests(influencerId));
    }

    @GetMapping("/outgoing")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<List<CollaborationResponse>> getOutgoingRequests(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long sellerId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(collaborationService.getMyOutgoingRequests(sellerId));
    }

    @PatchMapping("/respond/{sellerId}")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<CollaborationResponse> respondToRequest(
            @PathVariable Long sellerId,
            @RequestParam CollaborationStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long influencerId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(
                collaborationService.respondToRequest(influencerId, sellerId, status));
    }
}
