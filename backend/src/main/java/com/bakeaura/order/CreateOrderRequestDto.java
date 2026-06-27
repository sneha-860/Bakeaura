package com.bakeaura.order;

import com.bakeaura.enums.OrderType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateOrderRequestDto {

    @NotNull
    private Long sellerId;

    @NotEmpty
    private List<@Valid OrderItemRequest> items;

    @NotNull
    private String deliveryAddress;

    @NotNull
    @DecimalMin(value = "-90.0", message = "must be greater than or equal to -90")
    @DecimalMax(value = "90.0", message = "must be less than or equal to 90")
    private Double deliveryLatitude;

    @NotNull
    @DecimalMin(value = "-180.0", message = "must be greater than or equal to -180")
    @DecimalMax(value = "180.0", message = "must be less than or equal to 180")
    private Double deliveryLongitude;

    private LocalDate scheduledDeliveryDate;

    @NotNull
    private OrderType orderType;

    private String referralCode;

    @Data
    public static class OrderItemRequest {
        @NotNull
        private Long productId;
        @NotNull
        @Min(1)
        private Integer quantity;
    }
}
