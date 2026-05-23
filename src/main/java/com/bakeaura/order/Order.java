package com.bakeaura.order;

import com.bakeaura.common.OrderStatus;
import jakarta.persistence.*;
import com.bakeaura.user.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")     // "order" is a reserved SQL keyword — always use "orders"
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

    // Estimated delivery time in minutes (set when order is confirmed)
    private Integer estimatedDeliveryMinutes;

    // Razorpay order ID returned when we create an order on their system
    private String razorpayOrderId;

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