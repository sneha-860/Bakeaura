package com.bakeaura.seller;

import com.bakeaura.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "seller_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "shop_name")
    private String shopName;

    @Column(name = "shop_bio", columnDefinition = "TEXT")
    private String shopBio;

    @Column(name = "delivery_radius_km")
    private Double deliveryRadiusKm;

    @Column(name = "is_open")
    private Boolean isOpen = false;

    @Column(name = "banner_image_url")
    private String bannerImageUrl;

    @Column(name = "total_ratings")
    private Integer totalRatings = 0;

    @Column(name = "average_rating")
    private Double averageRating = 0.0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}