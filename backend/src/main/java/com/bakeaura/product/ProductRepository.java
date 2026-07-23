package com.bakeaura.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    // JpaRepository gives you these for FREE:
    // save(product)         → INSERT or UPDATE
    // findById(id)          → SELECT WHERE id = ?
    // findAll()             → SELECT *
    // deleteById(id)        → DELETE WHERE id = ?
    // existsById(id)        → true/false

    // Custom queries — Spring builds the SQL from the method name!
    List<Product> findBySellerId(Long sellerId);
    // → SELECT * FROM products WHERE seller_id = ?

    List<Product> findByCategoryId(Long categoryId);

    boolean existsByCategoryId(Long categoryId);

    List<Product> findByIsAvailableTrue();
    Page<Product> findByIsAvailableTrue(Pageable pageable);
    // → SELECT * FROM products WHERE is_available = true

    // Search by name (case-insensitive)
    List<Product> findByNameContainingIgnoreCase(String keyword);
    Page<Product> findByNameContainingIgnoreCase(String keyword, Pageable pageable);
    // → SELECT * FROM products WHERE LOWER(name) LIKE LOWER('%keyword%')

    long countBySellerId(Long sellerId);
    // → SELECT COUNT(*) FROM products WHERE seller_id = ?

    @Query("SELECT p.seller.id, COUNT(p) FROM Product p WHERE p.seller.id IN :sellerIds GROUP BY p.seller.id")
    List<Object[]> countBySellerIds(@Param("sellerIds") Collection<Long> sellerIds);
}
