package com.bakeaura.review;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReviewSummaryDto {
    private Long sellerId;
    private Double averageRating;
    private Long reviewCount;
}

