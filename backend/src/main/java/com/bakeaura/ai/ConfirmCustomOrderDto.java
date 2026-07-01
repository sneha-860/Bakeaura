package com.bakeaura.ai;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ConfirmCustomOrderDto {
    private Long sellerId;
    private String designBrief;
    private String imageBase64;
    private String occasion;
    private Integer serves;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
}
