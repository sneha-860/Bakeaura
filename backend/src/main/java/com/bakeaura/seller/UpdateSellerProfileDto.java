package com.bakeaura.seller;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSellerProfileDto {

    @Size(max = 100, message = "Shop name must be 100 characters or fewer")
    private String shopName;

    @Size(max = 500, message = "Shop bio must be 500 characters or fewer")
    private String shopBio;

    @DecimalMin(value = "1.0", message = "Delivery radius must be at least 1 km")
    @DecimalMax(value = "50.0", message = "Delivery radius cannot exceed 50 km")
    private Double deliveryRadiusKm;

    @Size(max = 2048, message = "Banner image URL is too long")
    private String bannerImageUrl;
}
