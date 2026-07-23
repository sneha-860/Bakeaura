package com.bakeaura.review;

import com.bakeaura.order.Order;
import com.bakeaura.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findBySellerOrderByCreatedAtDesc(User seller);

    List<Review> findBySellerOrderByCreatedAtDesc(User seller, Pageable pageable);

    Optional<Review> findByCustomerAndOrder(User customer, Order order);

    Long countBySeller(User seller);

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.seller = :seller")
    Double averageRatingForSeller(User seller);

    // Single GROUP BY query for feed ranking — replaces N per-seller lookups
    @Query("select r.seller.id, coalesce(avg(r.rating), 0.0) from Review r where r.seller.id in :sellerIds group by r.seller.id")
    List<Object[]> averageRatingsBatch(@Param("sellerIds") Collection<Long> sellerIds);

    // Returns [sellerId, avgRating, reviewCount] — used in seller listings to avoid N+1
    @Query("select r.seller.id, coalesce(avg(r.rating), 0.0), count(r) from Review r where r.seller.id in :sellerIds group by r.seller.id")
    List<Object[]> summaryBatch(@Param("sellerIds") Collection<Long> sellerIds);

    boolean existsByCustomerAndOrder(User customer, Order order);
}