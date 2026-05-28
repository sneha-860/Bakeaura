package com.bakeaura.websocket;

import com.bakeaura.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class OrderStatusMessageDto {
    private Long orderId;
    private OrderStatus status;
    private String message;
    private LocalDateTime timestamp;
}