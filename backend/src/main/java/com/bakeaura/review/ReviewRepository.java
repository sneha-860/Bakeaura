package com.bakeaura.review;

import com.bakeaura.order.Order;
import com.bakeaura.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findBySellerOrderByCreatedAtDesc(User seller);

    Optional<Review> findByCustomerAndOrder(User customer, Order order);

    Long countBySeller(User seller);

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.seller = :seller")
    Double averageRatingForSeller(User seller);

    boolean existsByCustomerAndOrder(User customer, Order order);
}