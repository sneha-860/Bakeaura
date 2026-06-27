package com.bakeaura.influencer;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InfluencerProfileUpdateRequest {
    private String niche;
    private String instagramUrl;
    private String youtubeUrl;
    private Integer followerCount;
}
