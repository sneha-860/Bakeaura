package com.bakeaura.auth;

import com.bakeaura.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private Long id;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private String email;
    private String name;
    private Role role;
    private boolean emailVerified;
}