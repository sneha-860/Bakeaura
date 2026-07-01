package com.bakeaura.customorder;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class SubmitCustomOrderDto {
    private Long sellerId;
    private String designBrief;
    private String occasion;
    private Integer serves;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
}
