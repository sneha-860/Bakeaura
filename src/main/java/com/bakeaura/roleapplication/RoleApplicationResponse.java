package com.bakeaura.roleapplication;

import com.bakeaura.enums.ApplicationStatus;
import com.bakeaura.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class RoleApplicationResponse {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private Role currentRole;
    private Role requestedRole;
    private ApplicationStatus status;
    private String message;
    private String reviewNote;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
