package com.bakeaura.payment;

import com.bakeaura.enums.PaymentStatus;
import com.bakeaura.payment.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    // Acquires an exclusive row-level lock so concurrent verifyPayment + webhook calls
    // on the same payment are serialized at the DB level, not just the application level.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findByOrder_Id(Long orderId);
    long countByStatus(PaymentStatus status);
}