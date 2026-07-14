package com.bakeaura.favorite;

import com.bakeaura.product.Product;
import com.bakeaura.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserAndProductIsNotNullOrderByCreatedAtDesc(User user);
    List<Favorite> findByUserAndSellerIsNotNullOrderByCreatedAtDesc(User user);
    Optional<Favorite> findByUserAndProduct(User user, Product product);
    Optional<Favorite> findByUserAndSeller(User user, User seller);
    boolean existsByUserAndProduct(User user, Product product);
    boolean existsByUserAndSeller(User user, User seller);
}
