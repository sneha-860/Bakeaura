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

    // Fields from User
    private Long id;
    private String name;
    private String email;

    // Fields from InfluencerProfile
    private String niche;
    private String instagramUrl;
    private String youtubeUrl;
    private Integer followerCount;
}
