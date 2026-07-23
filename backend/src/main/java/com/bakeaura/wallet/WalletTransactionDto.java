package com.bakeaura.wallet;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class WalletTransactionDto {
    private Long id;
    private Long influencerId;
    private BigDecimal amount;
    private String type;
    private String description;
    private LocalDateTime createdAt;
}
