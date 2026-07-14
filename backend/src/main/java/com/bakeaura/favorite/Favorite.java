package com.bakeaura.favorite;

import com.bakeaura.product.Product;
import com.bakeaura.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Represents either a product favourite (product non-null, seller null)
// or a seller favourite (seller non-null, product null). Exactly one must be set.
// Uniqueness enforced at service layer (existsByUserAndProduct / existsByUserAndSeller).
@Entity
@Table(name = "favorites")
@Data
@NoArgsConstructor
public class Favorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "seller_id", nullable = true)
    private User seller;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
