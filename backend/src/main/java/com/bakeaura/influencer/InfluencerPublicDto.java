package com.bakeaura.influencer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfluencerPublicDto {

    private Long id;
    private String name;

    // email deliberately omitted — public directory must not expose PII
    private String profileImageUrl;
    private String bio;
    private String niche;
    private String instagramUrl;
    private String youtubeUrl;
    private Integer followerCount;
    private Long totalReferrals;
    private String referralCode;
}
