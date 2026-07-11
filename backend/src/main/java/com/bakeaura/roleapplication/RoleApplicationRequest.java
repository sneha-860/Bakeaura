package com.bakeaura.roleapplication;

import com.bakeaura.enums.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RoleApplicationRequest {
    @NotNull
    private Role requestedRole;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone must be 10–15 digits")
    private String phone;

    @NotBlank(message = "Please describe your experience — admins read this to decide your application")
    @Size(min = 30, max = 1000, message = "Message must be at least 30 characters")
    private String message;

    // Required for INFLUENCER applications — validated in service
    private String socialUrl;

    @Min(value = 0, message = "Follower count cannot be negative")
    private Integer followerCount;
}
