package com.bakeaura.ai;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ConfirmCustomOrderDto {

    @NotNull(message = "sellerId is required")
    private Long sellerId;

    @NotBlank(message = "designBrief is required")
    private String designBrief;

    @NotBlank(message = "imageBase64 is required")
    private String imageBase64;

    @NotBlank(message = "occasion is required")
    private String occasion;

    @NotNull(message = "serves is required")
    @Min(value = 1, message = "serves must be at least 1")
    private Integer serves;

    @NotNull(message = "budgetMin is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "budgetMin must be greater than 0")
    private BigDecimal budgetMin;

    @NotNull(message = "budgetMax is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "budgetMax must be greater than 0")
    private BigDecimal budgetMax;
}
