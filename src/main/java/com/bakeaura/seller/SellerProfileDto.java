package com.bakeaura.seller;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SellerProfileDto {
    private Long id;
    private String name;
    private String email;
    private Double latitude;
    private Double longitude;
    private Long productCount;
}
