package com.bakeaura.influencer;

import com.bakeaura.enums.CollaborationStatus;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.notification.NotificationService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InfluencerCollaborationService {

    private final InfluencerCollaborationRepository collaborationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public CollaborationResponse requestCollaboration(Long sellerId, Long influencerId,
                                                      String message) {

        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));

        if (!seller.getRole().equals(Role.SELLER)) {
            throw new AccessDeniedException("Only sellers can request collaborations");
        }

        User influencer = userRepository.findById(influencerId)
                .orElseThrow(() -> new ResourceNotFoundException("Influencer not found"));

        if (!influencer.getRole().equals(Role.INFLUENCER)) {
            throw new BadRequestException("Target user is not an influencer");
        }

        if (collaborationRepository.existsByInfluencerIdAndSellerIdAndStatusIn(
                influencerId, sellerId,
                List.of(CollaborationStatus.PENDING, CollaborationStatus.APPROVED))) {
            throw new BadRequestException("An active collaboration request already exists with this influencer");
        }

        InfluencerCollaboration collaboration = new InfluencerCollaboration();
        collaboration.setInfluencerId(influencerId);
        collaboration.setSellerId(sellerId);
        collaboration.setMessage(message);

        CollaborationResponse response = toResponse(collaborationRepository.save(collaboration));

        notificationService.notifyUser(
                influencerId,
                "COLLAB_REQUEST",
                seller.getName() + " has sent you a collaboration request.",
                collaboration.getId()
        );

        return response;
    }

    public long countApprovedCollaborations(Long influencerId) {
        return collaborationRepository.countByInfluencerIdAndStatus(influencerId, CollaborationStatus.APPROVED);
    }

    public List<CollaborationResponse> getMyIncomingRequests(Long influencerId) {
        return collaborationRepository
                .findByInfluencerIdOrderByCreatedAtDesc(influencerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<CollaborationResponse> getMyOutgoingRequests(Long sellerId) {
        return collaborationRepository
                .findBySellerIdOrderByCreatedAtDesc(sellerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CollaborationResponse respondToRequest(Long influencerId, Long sellerId,
                                                  CollaborationStatus newStatus) {

        if (newStatus != CollaborationStatus.APPROVED && newStatus != CollaborationStatus.REJECTED) {
            throw new BadRequestException("Response must be APPROVED or REJECTED");
        }

        InfluencerCollaboration collaboration = collaborationRepository
                .findFirstByInfluencerIdAndSellerIdAndStatusOrderByCreatedAtDesc(
                        influencerId, sellerId, CollaborationStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration request not found"));

        collaboration.setStatus(newStatus);
        CollaborationResponse response = toResponse(collaborationRepository.save(collaboration));

        String influencerName = userRepository.findById(influencerId)
                .map(User::getName)
                .orElse("The influencer");
        notificationService.notifyUser(
                sellerId,
                newStatus == CollaborationStatus.APPROVED ? "COLLAB_APPROVED" : "COLLAB_REJECTED",
                influencerName + " has " + (newStatus == CollaborationStatus.APPROVED ? "accepted" : "declined") + " your collaboration request.",
                collaboration.getId()
        );

        return response;
    }

    private CollaborationResponse toResponse(InfluencerCollaboration c) {
        return new CollaborationResponse(
                c.getId(),
                c.getInfluencerId(),
                c.getSellerId(),
                c.getStatus(),
                c.getMessage(),
                c.getCreatedAt()
        );
    }
}