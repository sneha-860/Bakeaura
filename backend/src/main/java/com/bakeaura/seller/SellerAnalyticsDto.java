package com.bakeaura.seller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerAnalyticsDto {

    private BigDecimal totalRevenueAllTime;
    private BigDecimal totalRevenueThisMonth;
    private BigDecimal totalRevenueThisWeek;

    private Map<String, Long> orderCountsByStatus;

    private BigDecimal averageOrderValue;

    private String bestSellingProductName;
    private Long bestSellingProductQuantity;

    private Double averageRating;
    private Integer totalRatings;
}