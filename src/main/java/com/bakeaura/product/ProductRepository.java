package com.bakeaura.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
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
    // → SELECT * FROM products WHERE is_available = true

    // Search by name (case-insensitive)
    List<Product> findByNameContainingIgnoreCase(String keyword);
    // → SELECT * FROM products WHERE LOWER(name) LIKE LOWER('%keyword%')
}
