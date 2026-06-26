package com.bakeaura.order;

import com.bakeaura.enums.OrderType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateOrderFromCartRequestDto {
    @NotNull
    private Long sellerId;

    @NotNull
    private String deliveryAddress;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double deliveryLatitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double deliveryLongitude;

    private LocalDate scheduledDeliveryDate;

    @NotNull
    private OrderType orderType;
}
