package com.bakeaura.referral;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ReferralCodeResponse {
    private Long id;
    private Long influencerId;
    private String influencerName;
    private String code;
    private Boolean isActive;
    private LocalDateTime createdAt;
}