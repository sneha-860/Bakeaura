package com.bakeaura.influencer;

import com.bakeaura.enums.CollaborationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InfluencerCollaborationRepository
        extends JpaRepository<InfluencerCollaboration, Long> {

    boolean existsByInfluencerIdAndSellerIdAndStatusIn(
            Long influencerId, Long sellerId, List<CollaborationStatus> statuses);

    List<InfluencerCollaboration> findByInfluencerIdOrderByCreatedAtDesc(Long influencerId);

    List<InfluencerCollaboration> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    Optional<InfluencerCollaboration> findFirstByInfluencerIdAndSellerIdAndStatusOrderByCreatedAtDesc(
            Long influencerId, Long sellerId, CollaborationStatus status);

    long countByInfluencerIdAndStatus(Long influencerId, CollaborationStatus status);
}

