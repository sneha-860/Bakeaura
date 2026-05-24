package com.bakeaura.websocket;

import com.bakeaura.websocket.OrderStatusMessageDto;
import com.bakeaura.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderTrackingService {

    // SimpMessagingTemplate is the Spring abstraction for sending messages to WebSocket destinations
    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastStatusUpdate(Long orderId, OrderStatus newStatus) {

        String destination = "/topic/order/" + orderId;

        OrderStatusMessageDto message = new OrderStatusMessageDto(
                orderId,
                newStatus,
                buildStatusMessage(newStatus),
                LocalDateTime.now()
        );

        messagingTemplate.convertAndSend(destination, message);

        log.info("Broadcast status update for order {}: {}", orderId, newStatus);
    }

    private String buildStatusMessage(OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> "Your order has been confirmed by the seller!";
            case PREPARING -> "The baker has started preparing your order.";
            case OUT_FOR_DELIVERY -> "Your order is out for delivery!";
            case DELIVERED -> "Your order has been delivered. Enjoy!";
            case CANCELLED -> "Your order has been cancelled.";
            default -> "Order status updated.";
        };
    }
}
