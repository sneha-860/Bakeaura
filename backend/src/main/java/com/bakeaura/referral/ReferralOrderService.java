package com.bakeaura.referral;

import com.bakeaura.influencer.InfluencerProfileService;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public void processReferral(Long orderId, String code, BigDecimal orderTotal) {

        if (referralOrderRepository.existsByOrderId(orderId)) {
            return;
        }

        Optional<ReferralCode> optionalCode = referralCodeRepository.findByCode(code);
        if (optionalCode.isEmpty() || !optionalCode.get().isActive()) {
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

        influencerProfileService.incrementTotalReferrals(referralCode.getInfluencerId());

        // TODO: Step 15 — call WalletService to credit commission to influencer
        // walletService.credit(referralCode.getInfluencerId(), commission, "Referral commission for order #" + orderId);
    }
}
