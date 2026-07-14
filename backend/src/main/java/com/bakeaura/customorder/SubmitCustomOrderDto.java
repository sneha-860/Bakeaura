package com.bakeaura.customorder;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class SubmitCustomOrderDto {
    @NotNull
    private Long sellerId;

    @NotBlank
    @Size(max = 5000)
    private String designBrief;

    @NotBlank
    private String occasion;

    @NotNull
    @Min(value = 1)
    private Integer serves;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal budgetMin;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal budgetMax;
}
