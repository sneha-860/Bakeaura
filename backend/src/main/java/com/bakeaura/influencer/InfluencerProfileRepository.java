package com.bakeaura.influencer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InfluencerProfileRepository extends JpaRepository<InfluencerProfile, Long> {

    Optional<InfluencerProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
