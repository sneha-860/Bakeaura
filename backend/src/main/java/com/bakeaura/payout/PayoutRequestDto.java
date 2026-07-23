package com.bakeaura.payout;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PayoutRequestDto {
    private Long id;
    private Long influencerId;
    private BigDecimal amount;
    private String status;
    private String upiId;
    private String adminNote;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
}
