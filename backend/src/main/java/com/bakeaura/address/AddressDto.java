package com.bakeaura.address;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AddressDto {
    private Long id;
    private String label;
    private String addressLine;
    private Double latitude;
    private Double longitude;
    private Boolean defaultAddress;
}
