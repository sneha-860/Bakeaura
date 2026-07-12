package com.bakeaura.referral;

import com.bakeaura.influencer.InfluencerProfileService;
import com.bakeaura.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bakeaura.enums.CollaborationStatus;
import com.bakeaura.influencer.InfluencerCollaborationService;
import com.bakeaura.influencer.InfluencerProfileResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReferralOrderService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.10");

    private final ReferralOrderRepository referralOrderRepository;
    private final ReferralCodeRepository referralCodeRepository;
    private final InfluencerProfileService influencerProfileService;
    private final WalletService walletService;
    private final InfluencerCollaborationService influencerCollaborationService;

    @Transactional
    public void processReferral(Long orderId, String code, BigDecimal orderTotal) {

        if (referralOrderRepository.existsByOrderId(orderId)) {
            return;
        }

        Optional<ReferralCode> optionalCode = referralCodeRepository.findByCode(code);
        if (optionalCode.isEmpty() || !Boolean.TRUE.equals(optionalCode.get().getIsActive())) {
            return;
        }

        ReferralCode referralCode = optionalCode.get();

        BigDecimal commission = orderTotal
                .multiply(COMMISSION_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        ReferralOrder referralOrder = new ReferralOrder();
        referralOrder.setReferralCode(referralCode);
        referralOrder.setOrderId(orderId);
        referralOrder.setCommissionAmount(commission);

        referralOrderRepository.save(referralOrder);

        Long influencerId = referralCode.getInfluencer().getId();

        influencerProfileService.incrementTotalReferrals(influencerId);

        walletService.credit(
                influencerId,
                commission,
                "Referral commission for order #" + orderId
        );
    }

    public List<ReferralOrder> getReferralOrdersByInfluencer(Long influencerId) {
        return referralOrderRepository.findByReferralCode_Influencer_IdOrderByCreatedAtDesc(influencerId);
    }

    public InfluencerAnalyticsDto getDashboardAnalytics(Long influencerId) {
        InfluencerProfileResponse profile = influencerProfileService.getMyProfile(influencerId);

        BigDecimal walletBalance = walletService.getBalance(influencerId);

        List<ReferralOrder> referralOrders = getReferralOrdersByInfluencer(influencerId);

        List<InfluencerAnalyticsDto.RecentReferralOrder> recentOrders = referralOrders.stream()
                .limit(10)
                .map(ro -> InfluencerAnalyticsDto.RecentReferralOrder.builder()
                        .orderId(ro.getOrderId())
                        .commissionAmount(ro.getCommissionAmount())
                        .createdAt(ro.getCreatedAt())
                        .build())
                .toList();

        long activeCollaborationsCount = influencerCollaborationService.getMyIncomingRequests(influencerId)
                .stream()
                .filter(c -> c.getStatus() == CollaborationStatus.APPROVED)
                .count();

        return InfluencerAnalyticsDto.builder()
                .totalEarnings(profile.getTotalEarnings())
                .walletBalance(walletBalance)
                .totalReferralOrders((long) referralOrders.size())
                .recentReferralOrders(recentOrders)
                .activeCollaborationsCount(activeCollaborationsCount)
                .build();
    }
}