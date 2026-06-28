package com.bakeaura.user;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Profile fetched", userService.getMe(Long.parseLong(authentication.getName()))));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> updateMe(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Profile updated",
                userService.updateProfile(Long.parseLong(authentication.getName()), request)
        ));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(Long.parseLong(authentication.getName()), request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed", null));
    }

    @PostMapping("/me/change-email")
    public ResponseEntity<ApiResponse<Void>> requestEmailChange(
            Authentication authentication,
            @Valid @RequestBody ChangeEmailRequest request) {
        userService.requestEmailChange(Long.parseLong(authentication.getName()), request);
        return ResponseEntity.ok(ApiResponse.ok("Verification email sent to your new address", null));
    }
}
