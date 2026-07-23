package com.bakeaura.websocket;

import com.bakeaura.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class OrderTrackingController {

    private final OrderTrackingService orderTrackingService;
    private final OrderService orderService;

    @MessageMapping("/order/{orderId}/join")
    @SendTo("/topic/order/{orderId}")
    public OrderStatusMessageDto joinOrderRoom(@DestinationVariable Long orderId, Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required");
        }
        Long userId = Long.parseLong(principal.getName());
        if (!orderService.isOrderParticipant(orderId, userId)) {
            throw new AccessDeniedException("Not authorised to track this order");
        }
        return new OrderStatusMessageDto(orderId, null,
                "Connected to order tracking for order #" + orderId, LocalDateTime.now());
    }
}
