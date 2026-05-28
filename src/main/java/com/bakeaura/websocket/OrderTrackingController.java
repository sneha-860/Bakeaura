package com.bakeaura.websocket;

import com.bakeaura.websocket.OrderStatusMessageDto;
import com.bakeaura.enums.OrderStatus;
import com.bakeaura.websocket.OrderTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class OrderTrackingController {

    private final OrderTrackingService orderTrackingService;

    // Client can send a "join" message to /app/order/{orderId}/join
    // Server echoes back the current topic — useful for connection confirmation
    @MessageMapping("/order/{orderId}/join")
    @SendTo("/topic/order/{orderId}")
    public OrderStatusMessageDto joinOrderRoom(@DestinationVariable Long orderId) {
        return new OrderStatusMessageDto(
                orderId,
                null,
                "Connected to order tracking for order #" + orderId,
                java.time.LocalDateTime.now()
        );
    }
}
