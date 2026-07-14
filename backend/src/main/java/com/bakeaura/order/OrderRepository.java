package com.bakeaura.order;

import com.bakeaura.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.seller.id = :sellerId ORDER BY o.createdAt DESC")
    List<Order> findBySeller_IdOrderByCreatedAtDesc(Long sellerId);

    List<Order> findBySeller_IdAndStatusOrderByCreatedAtDesc(Long sellerId, OrderStatus status);

    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    @Query("SELECT o FROM Order o JOIN FETCH o.items i JOIN FETCH i.product WHERE o.id = :id")
    Optional<Order> findByIdWithItems(Long id);

    Page<Order> findByCustomer_IdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Order> findBySeller_IdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    Page<Order> findBySeller_IdAndStatusOrderByCreatedAtDesc(
            Long sellerId, OrderStatus status, Pageable pageable);

    long countByStatusNot(OrderStatus status);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.seller.id = :sellerId AND o.status NOT IN (com.bakeaura.enums.OrderStatus.PENDING, com.bakeaura.enums.OrderStatus.CANCELLED)")
    BigDecimal sumRevenueBySellerExcludingPendingAndCancelled(@Param("sellerId") Long sellerId);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.seller.id = :sellerId AND o.status NOT IN (com.bakeaura.enums.OrderStatus.PENDING, com.bakeaura.enums.OrderStatus.CANCELLED) AND o.createdAt >= :from")
    BigDecimal sumRevenueBySellerSince(@Param("sellerId") Long sellerId, @Param("from") LocalDateTime from);

    @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.seller.id = :sellerId GROUP BY o.status")
    List<Object[]> countOrdersByStatusForSeller(@Param("sellerId") Long sellerId);

    @Query("SELECT i.product.name, SUM(i.quantity) FROM OrderItem i WHERE i.order.seller.id = :sellerId AND i.order.status NOT IN (com.bakeaura.enums.OrderStatus.PENDING, com.bakeaura.enums.OrderStatus.CANCELLED) GROUP BY i.product.name ORDER BY SUM(i.quantity) DESC")
    Page<Object[]> findBestSellingProductsBySeller(@Param("sellerId") Long sellerId, Pageable pageable);
}