package com.bakeaura.reel;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReelResponseDTO {
    private Long id;
    private Long sellerId;
    private String sellerName;
    private String caption;
    private String videoUrl;
    private String thumbnailUrl;
    private Integer durationSeconds;
    private String status;          // "PROCESSING", "ACTIVE", "FAILED"
    private Long likeCount;
    private Long commentCount;
    private Long saveCount;
    private Long viewCount;
    private LocalDateTime createdAt;
}
