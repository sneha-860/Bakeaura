package com.bakeaura.notification;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private String type;
    private String message;
    private Long relatedId;
    private Boolean read;
    private LocalDateTime createdAt;
}
