package com.bakeaura.order;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotNull
    private Long sellerId;

    @NotEmpty
    private List<OrderItemRequest> items;

    @NotNull
    private String deliveryAddress;

    @NotNull
    private Double deliveryLatitude;

    @NotNull
    private Double deliveryLongitude;

    @Data
    public static class OrderItemRequest {
        @NotNull
        private Long productId;
        @NotNull
        private Integer quantity;
    }
}