//DTO (Data Transfer Object) is a simple object used to transfer only the required data between layers of an application, especially between backend and frontend, without exposing the complete entity structure.

//DTO     -> Data sent to frontend
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
