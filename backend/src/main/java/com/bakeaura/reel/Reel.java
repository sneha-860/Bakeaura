package com.bakeaura.reel;



import com.bakeaura.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reels")
@Data
@NoArgsConstructor
public class Reel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The seller who uploaded this reel
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    // Caption/description the seller wrote
    @Column(length = 500)
    private String caption;

    // The Cloudinary video URL — what we show in the feed
    @Column(name = "video_url")
    private String videoUrl;

    // Cloudinary's ID for this file — needed if we want to delete it later
    @Column(name = "cloudinary_public_id")
    private String cloudinaryPublicId;

    // Auto-generated thumbnail from Cloudinary
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    // Duration in seconds (Cloudinary tells us this after upload)
    private Integer durationSeconds;

    // PROCESSING → the video is being uploaded to Cloudinary
    // ACTIVE     → upload done, visible in feed
    // FAILED     → upload failed
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReelStatus status = ReelStatus.PROCESSING;

    // Counters — we'll increment these when users interact
    @Column(nullable = false)
    private Long likeCount = 0L;

    @Column(nullable = false)
    private Long commentCount = 0L;

    @Column(nullable = false)
    private Long saveCount = 0L;

    @Column(nullable = false)
    private Long viewCount = 0L;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum ReelStatus {
        PROCESSING, ACTIVE, FAILED
    }
}
