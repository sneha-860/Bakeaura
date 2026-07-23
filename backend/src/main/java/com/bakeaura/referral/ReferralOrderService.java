package com.bakeaura.referral;

import com.bakeaura.influencer.InfluencerCollaborationService;
import com.bakeaura.influencer.InfluencerProfileService;
import com.bakeaura.notification.NotificationService;
import com.bakeaura.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final NotificationService notificationService;

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

        notificationService.notifyUser(
                influencerId,
                "REFERRAL_COMMISSION",
                "You earned ₹" + commission + " commission from referral order #" + orderId + ".",
                orderId
        );
    }

    public List<ReferralOrder> getReferralOrdersByInfluencer(Long influencerId) {
        return referralOrderRepository.findByReferralCode_Influencer_IdOrderByCreatedAtDesc(influencerId);
    }

    public List<ReferralOrderDto> getAuditRecords(Long influencerId) {
        return referralOrderRepository.findByReferralCode_Influencer_IdOrderByCreatedAtDesc(influencerId)
                .stream()
                .map(ro -> new ReferralOrderDto(
                        ro.getId(),
                        ro.getReferralCode().getInfluencer().getId(),
                        ro.getReferralCode().getCode(),
                        ro.getOrderId(),
                        ro.getCommissionAmount(),
                        ro.getCreatedAt()
                ))
                .toList();
    }

    public InfluencerAnalyticsDto getDashboardAnalytics(Long influencerId) {
        BigDecimal walletBalance = walletService.getBalance(influencerId);

        List<ReferralOrder> referralOrders = getReferralOrdersByInfluencer(influencerId);

        // Derive total earnings from actual referral commissions — never from the stale
        // InfluencerProfile.totalEarnings column which is never written.
        BigDecimal totalEarnings = referralOrders.stream()
                .map(ReferralOrder::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<InfluencerAnalyticsDto.RecentReferralOrder> recentOrders = referralOrders.stream()
                .limit(10)
                .map(ro -> InfluencerAnalyticsDto.RecentReferralOrder.builder()
                        .orderId(ro.getOrderId())
                        .commissionAmount(ro.getCommissionAmount())
                        .createdAt(ro.getCreatedAt())
                        .build())
                .toList();

        long activeCollaborationsCount = influencerCollaborationService
                .countApprovedCollaborations(influencerId);

        return InfluencerAnalyticsDto.builder()
                .totalEarnings(totalEarnings)
                .walletBalance(walletBalance)
                .totalReferralOrders((long) referralOrders.size())
                .recentReferralOrders(recentOrders)
                .activeCollaborationsCount(activeCollaborationsCount)
                .build();
    }
}
