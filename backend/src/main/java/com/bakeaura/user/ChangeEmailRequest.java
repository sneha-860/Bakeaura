package com.bakeaura.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeEmailRequest {

    @Email(message = "Must be a valid email address")
    @NotBlank(message = "New email is required")
    private String newEmail;

    @NotBlank(message = "Current password is required")
    private String currentPassword;
}
