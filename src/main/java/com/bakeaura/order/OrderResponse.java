package com.bakeaura.order;

import com.bakeaura.common.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private String customerName;
    private String sellerName;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String deliveryAddress;
    private Integer estimatedDeliveryMinutes;
    private String razorpayOrderId;
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
