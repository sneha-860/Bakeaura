package com.bakeaura.seller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerProfileDto {

    // Fields from User
    private Long id;
    private String name;
    // Exact coordinates deliberately excluded — home bakers' precise location is private.
    // Server-side Haversine calculations use coordinates directly from the User entity.

    // Fields from SellerProfile
    private String shopName;
    private String shopBio;
    private Double deliveryRadiusKm;
    private Boolean isOpen;
    private String bannerImageUrl;
    private Integer totalRatings;
    private Double averageRating;

    // Computed
    private Long productCount;

    // Profile completeness percentage (0-100)
    private Integer profileCompleteness;
}
