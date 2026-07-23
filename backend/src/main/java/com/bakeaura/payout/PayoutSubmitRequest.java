package com.bakeaura.payout;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayoutSubmitRequest {

    @NotNull
    @DecimalMin(value = "1.00", message = "Minimum payout amount is ₹1")
    private BigDecimal amount;

    @NotBlank(message = "UPI ID is required")
    private String upiId;
}
