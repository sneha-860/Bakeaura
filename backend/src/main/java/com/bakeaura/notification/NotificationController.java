package com.bakeaura.notification;

import com.bakeaura.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getNotifications(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Notifications fetched",
                notificationService.getNotifications(userId)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Unread count fetched", Map.of(
                "unreadCount", notificationService.getUnreadCount(Long.parseLong(authentication.getName()))
        )));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationDto>> markRead(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Notification marked read", notificationService.markRead(Long.parseLong(authentication.getName()),id)));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(Authentication authentication) {
        notificationService.markAllRead(Long.parseLong(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.ok("Notifications marked read", null));
    }
}
