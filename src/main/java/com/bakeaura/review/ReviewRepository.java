package com.bakeaura.review;

import com.bakeaura.product.Product;
import com.bakeaura.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductOrderByCreatedAtDesc(Product product);
    Optional<Review> findByUserAndProduct(User user, Product product);
    Long countByProduct(Product product);

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.product = :product")
    Double averageRatingForProduct(Product product);
}
