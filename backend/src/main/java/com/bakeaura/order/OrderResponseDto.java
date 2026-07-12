package com.bakeaura.order;

import com.bakeaura.enums.OrderStatus;
import com.bakeaura.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponseDto {
    private Long id;
    private Long sellerId;
    private String customerName;
    private String sellerName;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String deliveryAddress;
    private String deliveryInstructions;
    private Integer estimatedDeliveryMinutes;
    private String razorpayOrderId;
    private PaymentStatus paymentStatus;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class OrderItemResponse {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal priceAtPurchase;
        private BigDecimal subtotal;
    }
}
