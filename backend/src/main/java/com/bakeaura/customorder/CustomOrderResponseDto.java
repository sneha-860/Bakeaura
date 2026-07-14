package com.bakeaura.customorder;

import com.bakeaura.enums.CustomOrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CustomOrderResponseDto {
    private Long id;
    private Long customerId;
    private Long sellerId;
    private String designBrief;
    private String generatedImageUrl;
    private String occasion;
    private Integer serves;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private CustomOrderStatus status;
    private BigDecimal sellerQuote;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
