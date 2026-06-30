package com.bakeaura.referral;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfluencerAnalyticsDto {

    private BigDecimal totalEarnings;
    private BigDecimal walletBalance;
    private Long totalReferralOrders;
    private List<RecentReferralOrder> recentReferralOrders;
    private Long activeCollaborationsCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentReferralOrder {
        private Long orderId;
        private BigDecimal commissionAmount;
        private LocalDateTime createdAt;
    }
}

