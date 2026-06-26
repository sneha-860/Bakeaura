package com.bakeaura.seller;

import lombok.Data;

@Data
public class UpdateSellerProfileDto {
    private String shopName;
    private String shopBio;
    private Double deliveryRadiusKm;
    private String bannerImageUrl;
}
