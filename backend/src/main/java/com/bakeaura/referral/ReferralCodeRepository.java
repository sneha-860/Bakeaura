package com.bakeaura.referral;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {

    Optional<ReferralCode> findByCode(String code);

    List<ReferralCode> findByInfluencerIdAndIsActiveTrue(Long influencerId);

    boolean existsByCode(String code);
}
