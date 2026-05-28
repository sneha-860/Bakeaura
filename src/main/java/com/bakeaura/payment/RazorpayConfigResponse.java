package com.bakeaura.payment;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RazorpayConfigResponse {
    private String keyId;
    private String currency;
}
