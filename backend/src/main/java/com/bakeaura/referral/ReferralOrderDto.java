package com.bakeaura.referral;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReferralOrderDto(
        Long id,
        Long influencerId,
        String referralCode,
        Long orderId,
        BigDecimal commissionAmount,
        LocalDateTime createdAt
) {}
