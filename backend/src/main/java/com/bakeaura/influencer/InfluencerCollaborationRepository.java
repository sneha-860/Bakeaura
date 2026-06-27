package com.bakeaura.influencer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InfluencerCollaborationRepository
        extends JpaRepository<InfluencerCollaboration, Long> {

    boolean existsByInfluencerIdAndSellerId(Long influencerId, Long sellerId);

    List<InfluencerCollaboration> findByInfluencerIdOrderByCreatedAtDesc(Long influencerId);

    List<InfluencerCollaboration> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    Optional<InfluencerCollaboration> findByInfluencerIdAndSellerId(
            Long influencerId, Long sellerId);
}

