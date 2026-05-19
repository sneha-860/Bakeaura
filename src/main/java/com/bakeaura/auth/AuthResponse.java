package com.bakeaura.auth;

import com.bakeaura.common.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String tokenType;
    private String email;
    private Role role;
}
