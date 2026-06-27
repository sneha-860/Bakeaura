package com.bakeaura.payout;

import com.bakeaura.enums.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {

    List<PayoutRequest> findByInfluencerIdOrderByCreatedAtDesc(Long influencerId);

    List<PayoutRequest> findByStatusOrderByCreatedAtAsc(PayoutStatus status);

    boolean existsByInfluencerIdAndStatus(Long influencerId, PayoutStatus status);
}