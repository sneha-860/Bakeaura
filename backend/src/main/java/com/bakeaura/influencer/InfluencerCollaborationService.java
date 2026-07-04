package com.bakeaura.influencer;

import com.bakeaura.enums.CollaborationStatus;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
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

    @Transactional
    public CollaborationResponse requestCollaboration(Long sellerId, Long influencerId,
                                                      String message) {

        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));

        if (!seller.getRole().equals(Role.SELLER)) {
            throw new BadRequestException("Only sellers can request collaborations");
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

        return toResponse(collaborationRepository.save(collaboration));
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

        if (!collaboration.getInfluencerId().equals(influencerId)) {
            throw new AccessDeniedException("You are not authorised to respond to this request");
        }

        if (collaboration.getStatus() != CollaborationStatus.PENDING) {
            throw new BadRequestException("This request has already been responded to");
        }

        collaboration.setStatus(newStatus);
        return toResponse(collaborationRepository.save(collaboration));
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