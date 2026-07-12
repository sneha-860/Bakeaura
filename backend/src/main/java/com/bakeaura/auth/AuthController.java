package com.bakeaura.auth;

import com.bakeaura.common.ApiResponse;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.user.UserService;
import com.bakeaura.user.ChangeEmailRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserService userService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("User registered", authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful", authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.ok("Logged out", null));
    }

    // Email link points to this backend endpoint.
    // Backend verifies the token and redirects the browser to the frontend.
    // This avoids Gmail stripping query params from localhost links during its safety scan.
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/verify-email?verified=true"))
                    .build();
        } catch (BadRequestException e) {
            String code = e.getMessage().toLowerCase().contains("expired") ? "expired" : "invalid";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/verify-email?error=" + code))
                    .build();
        }
    }

    @GetMapping("/verify-email-change")
    public ResponseEntity<Void> verifyEmailChange(@RequestParam String token) {
        try {
            userService.confirmEmailChange(token);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/verify-email-change?verified=true"))
                    .build();
        } catch (BadRequestException e) {
            String code = e.getMessage().toLowerCase().contains("expired") ? "expired" : "invalid";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/verify-email-change?error=" + code))
                    .build();
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("If this email exists and is unverified, a new link has been sent.", null));
    }
}
