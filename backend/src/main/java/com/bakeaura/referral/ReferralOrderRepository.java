package com.bakeaura.referral;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferralOrderRepository extends JpaRepository<ReferralOrder, Long> {

    boolean existsByOrderId(Long orderId);

    List<ReferralOrder> findByReferralCode_Id(Long referralCodeId);

    List<ReferralOrder> findByReferralCode_Influencer_IdOrderByCreatedAtDesc(Long influencerId);
}
