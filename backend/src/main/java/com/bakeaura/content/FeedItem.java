package com.bakeaura.content;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FeedItem {

    private Long id;
    private String videoUrl;
    private String thumbnailUrl;
    private String caption;
    private String sellerName;
    private Long sellerId;
    private Long likeCount;
    private Long viewCount;
    private Long commentCount;
    private Double averageRating;
    private double score;
}