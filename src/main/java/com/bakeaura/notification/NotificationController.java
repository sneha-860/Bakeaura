package com.bakeaura.notification;

import com.bakeaura.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getNotifications(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Notifications fetched", notificationService.getNotifications(authentication.getName())));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Unread count fetched", Map.of(
                "unreadCount", notificationService.getUnreadCount(authentication.getName())
        )));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationDto>> markRead(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Notification marked read", notificationService.markRead(authentication.getName(), id)));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(Authentication authentication) {
        notificationService.markAllRead(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Notifications marked read", null));
    }
}
