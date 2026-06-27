package com.bakeaura.customorder;

import com.bakeaura.enums.CustomOrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "custom_order_requests")
@Getter
@Setter
public class CustomOrderRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "design_brief", nullable = false, columnDefinition = "TEXT")
    private String designBrief;

    @Column(name = "generated_image_url")
    private String generatedImageUrl;

    @Column(nullable = false)
    private String occasion;

    @Column(nullable = false)
    private Integer serves;

    @Column(name = "budget_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal budgetMin;

    @Column(name = "budget_max", nullable = false, precision = 10, scale = 2)
    private BigDecimal budgetMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomOrderStatus status;

    @Column(name = "seller_quote", precision = 10, scale = 2)
    private BigDecimal sellerQuote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.status = CustomOrderStatus.PENDING;
    }
}