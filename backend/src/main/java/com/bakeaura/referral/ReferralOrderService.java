package com.bakeaura.referral;

import com.bakeaura.influencer.InfluencerProfileService;
import com.bakeaura.order.OrderCreatedEvent;
import com.bakeaura.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReferralOrderService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.10");

    private final ReferralOrderRepository referralOrderRepository;
    private final ReferralCodeRepository referralCodeRepository;
    private final InfluencerProfileService influencerProfileService;
    private final WalletService walletService;

    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        String code = event.getReferralCode();
        if (code == null || code.isBlank()) {
            return;
        }
        processReferral(
                event.getOrder().getId(),
                code,
                event.getOrder().getTotalAmount()
        );
    }

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
}