package com.bakeaura.influencer;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class InfluencerProfileResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String niche;
    private String instagramUrl;
    private String youtubeUrl;
    private Integer followerCount;
    private Long totalReferrals;
    private BigDecimal totalEarnings;
    private LocalDateTime createdAt;
}
