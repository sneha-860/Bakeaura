package com.bakeaura.review;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReviewSummaryDto {
    private Long sellerId;
    private Double averageRating;
    private Long reviewCount;
}

