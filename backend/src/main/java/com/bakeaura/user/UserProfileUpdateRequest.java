package com.bakeaura.user;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    @NotBlank
    private String name;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double latitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double longitude;

    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone must be 10–15 digits")
    private String phone;

    @Size(max = 500, message = "Bio must be 500 characters or less")
    private String bio;
}
