package com.bakeaura.influencer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InfluencerProfileRepository extends JpaRepository<InfluencerProfile, Long> {

    Optional<InfluencerProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Modifying
    @Query("UPDATE InfluencerProfile p SET p.totalReferrals = p.totalReferrals + 1 WHERE p.user.id = :userId")
    void incrementTotalReferralsByUserId(@Param("userId") Long userId);
}
