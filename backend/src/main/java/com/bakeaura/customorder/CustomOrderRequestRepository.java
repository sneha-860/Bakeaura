package com.bakeaura.customorder;

import com.bakeaura.enums.CustomOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomOrderRequestRepository extends JpaRepository<CustomOrderRequest, Long> {

    List<CustomOrderRequest> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<CustomOrderRequest> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    List<CustomOrderRequest> findBySellerIdAndStatusOrderByCreatedAtAsc(
            Long sellerId, CustomOrderStatus status);

    boolean existsByCustomerIdAndSellerIdAndStatus(
            Long customerId, Long sellerId, CustomOrderStatus status);
}
