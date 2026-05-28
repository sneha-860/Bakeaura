package com.bakeaura.order;

import com.bakeaura.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    List<Order> findBySeller_IdOrderByCreatedAtDesc(Long sellerId);

    List<Order> findBySeller_IdAndStatusOrderByCreatedAtDesc(Long sellerId, OrderStatus status);

    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    // JPQL — JOIN FETCH avoids N+1 queries by loading items in one SQL query
    @Query("SELECT o FROM Order o JOIN FETCH o.items i JOIN FETCH i.product WHERE o.id = :id")
    java.util.Optional<Order> findByIdWithItems(Long id);
}
