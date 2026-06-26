package com.bakeaura.product;

import com.bakeaura.category.Category;
import com.bakeaura.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_seller_id", columnList = "seller_id"),
        @Index(name = "idx_product_category_id", columnList = "category_id")
})
@Data
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;


    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;
    // Always use BigDecimal for money — never double or float!
    // double has precision issues: 19.99 might store as 19.990000001

    private Integer stockQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    // This creates a seller_id column in the products table
    // FetchType.LAZY = don't load the Seller object unless we explicitly ask
    private User seller;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    private String imageUrl;

    @Column(name = "is_available")
    private Boolean isAvailable = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column (name = "is_pre_order_only")
    private Boolean isPreOrderOnly = false;

    @Column (name = "min_advance_days")
    private Integer minAdvanceDays;
}