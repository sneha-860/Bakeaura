package com.bakeaura.notification;

import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.order.OrderCreatedEvent;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<NotificationDto> getNotifications(Long userId) {
        User user = getUser(userId);
        return notificationRepository
                .findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserAndReadFalse(getUser(userId));
    }

    @Transactional
    public NotificationDto markRead(Long userId, Long id) {
        User user = getUser(userId);
        Notification notification = getOwnedNotification(id, user);
        notification.setRead(true);
        return toDto(notificationRepository.save(notification));
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllAsRead(getUser(userId));
    }

    @EventListener
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        com.bakeaura.order.Order order = event.getOrder();
        notifyUser(
                order.getSeller().getId(),
                "ORDER_CREATED",
                "New order #" + order.getId() + " has been placed.",
                order.getId()
        );
    }

    @Transactional
    public NotificationDto notifyUser(Long userId, String type, String message, Long relatedId) {
        User user = getUser(userId);
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setMessage(message);
        notification.setRelatedId(relatedId);
        NotificationDto dto = toDto(notificationRepository.save(notification));
        messagingTemplate.convertAndSend("/topic/users/" + user.getId() + "/notifications", dto);
        return dto;
    }

    private Notification getOwnedNotification(Long id, User user) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied");
        }
        return notification;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private NotificationDto toDto(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getRelatedId(),
                notification.getRead(),
                notification.getCreatedAt()
        );
    }
}
