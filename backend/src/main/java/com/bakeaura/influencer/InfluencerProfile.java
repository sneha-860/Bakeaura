package com.bakeaura.influencer;

import com.bakeaura.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "influencer_profiles")
@Getter
@Setter
@NoArgsConstructor
public class InfluencerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String niche;

    private String instagramUrl;

    private String youtubeUrl;

    private Integer followerCount;

    private Long totalReferrals;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalEarnings;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.totalReferrals == null) this.totalReferrals = 0L;
        if (this.totalEarnings == null) this.totalEarnings = BigDecimal.ZERO;
    }
}
