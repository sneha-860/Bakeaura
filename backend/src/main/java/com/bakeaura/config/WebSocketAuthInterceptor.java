package com.bakeaura.config;

import com.bakeaura.auth.JwtUtil;
import com.bakeaura.enums.Role;
import com.bakeaura.order.Order;
import com.bakeaura.order.OrderRepository;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    private static final Pattern USER_TOPIC = Pattern.compile("^/topic/users/(\\d+)/.*$");
    private static final Pattern ORDER_TOPIC = Pattern.compile("^/topic/order/(\\d+)$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            // Auth is mandatory — anonymous WebSocket connections are not permitted
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("WebSocket CONNECT rejected: missing Authorization header");
                throw new IllegalArgumentException("Authentication required to connect");
            }
            String token = authHeader.substring(7);
            if (!jwtUtil.isTokenValid(token) || !jwtUtil.isAccessToken(token)) {
                log.warn("WebSocket CONNECT rejected: invalid or non-access JWT");
                throw new IllegalArgumentException("Invalid or expired token");
            }
            Long userId = jwtUtil.extractUserId(token);
            Principal principal = new UsernamePasswordAuthenticationToken(
                    userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            accessor.setUser(principal);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            Principal user = accessor.getUser();

            if (destination == null) return message;

            Matcher userTopicMatcher = USER_TOPIC.matcher(destination);
            if (userTopicMatcher.matches()) {
                Long topicUserId = Long.parseLong(userTopicMatcher.group(1));
                if (user == null) {
                    log.warn("Unauthenticated subscription attempt to {}", destination);
                    throw new IllegalArgumentException("Authentication required to subscribe to user notifications");
                }
                Long subscriberId = Long.parseLong(user.getName());
                if (!subscriberId.equals(topicUserId)) {
                    log.warn("User {} attempted to subscribe to notifications for user {}", subscriberId, topicUserId);
                    throw new IllegalArgumentException("Not authorised to subscribe to another user's notifications");
                }
            }

            Matcher orderTopicMatcher = ORDER_TOPIC.matcher(destination);
            if (orderTopicMatcher.matches()) {
                if (user == null) {
                    log.warn("Unauthenticated subscription attempt to {}", destination);
                    throw new IllegalArgumentException("Authentication required to subscribe to order tracking");
                }
                Long subscriberId = Long.parseLong(user.getName());
                Long orderId = Long.parseLong(orderTopicMatcher.group(1));
                Optional<Order> orderOpt = orderRepository.findById(orderId);
                if (orderOpt.isEmpty()) {
                    log.warn("User {} attempted to subscribe to non-existent order {}", subscriberId, orderId);
                    throw new IllegalArgumentException("Order not found");
                }
                Order order = orderOpt.get();
                boolean isCustomer = order.getCustomer() != null
                        && order.getCustomer().getId().equals(subscriberId);
                boolean isSeller = order.getSeller() != null
                        && order.getSeller().getId().equals(subscriberId);
                boolean isAdmin = userRepository.findById(subscriberId)
                        .map(u -> u.getRole() == Role.ADMIN)
                        .orElse(false);
                if (!isCustomer && !isSeller && !isAdmin) {
                    log.warn("User {} is not authorised to track order {}", subscriberId, orderId);
                    throw new IllegalArgumentException("Not authorised to subscribe to this order's tracking");
                }
            }
        }

        return message;
    }
}
