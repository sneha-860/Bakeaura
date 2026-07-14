package com.bakeaura.order;

import com.bakeaura.enums.OrderStatus;
import com.bakeaura.enums.OrderType;
import com.bakeaura.enums.PaymentStatus;
import jakarta.persistence.*;
import com.bakeaura.user.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_customer_id", columnList = "customer_id"),
        @Index(name = "idx_order_seller_id", columnList = "seller_id"),
        @Index(name = "idx_order_status", columnList = "status")
})    // "order" is a reserved SQL keyword — always use "orders"
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The customer who placed the order
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    // The seller (baker) fulfilling the order
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false )
    private OrderType orderType;

    @Column (nullable = true)
    private LocalDate scheduledDeliveryDate;

    // Line items — cascade ALL means saving/deleting the order cascades to items
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)  // Store the enum name ("PENDING") not its ordinal (0)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // Delivery address fields (denormalised for immutability — address can change later)
    @Column(nullable = false)
    private String deliveryAddress;

    private Double deliveryLatitude;
    private Double deliveryLongitude;

    @Column(nullable = true, length = 500)
    private String deliveryInstructions;

    // Estimated delivery time in minutes (set when order is confirmed)
    private Integer estimatedDeliveryMinutes;

    // Razorpay order ID returned when we create an order on their system
    private String razorpayOrderId;

    // Referral code string applied at checkout — stored for commission processing after payment capture
    @Column(nullable = true)
    private String referralCode;

    // Denormalized from the payments table so OrderService.toResponse() can include payment
    // status without a cross-module PaymentRepository injection.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Helper method to add an item and wire up the bidirectional relationship
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}