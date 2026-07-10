package com.bakeaura.admin;

import com.bakeaura.common.ApiResponse;
import com.bakeaura.enums.Role;
import com.bakeaura.user.UserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardDto>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Dashboard fetched", adminService.dashboard()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsers(@RequestParam(required = false) Role role) {
        return ResponseEntity.ok(ApiResponse.ok("Users fetched", adminService.getUsers(role)));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<UserDto>> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserStatusRequest request,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("User status updated", adminService.updateUserStatus(adminId, id, request)));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<UserDto>> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("User role updated", adminService.updateUserRole(adminId, id, request.getRole())));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long id,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        adminService.deleteUser(adminId, id);
        return ResponseEntity.ok(ApiResponse.ok("User deleted", null));
    }

    @PostMapping("/cache/categories/evict")
    public ResponseEntity<ApiResponse<Void>> evictCategoriesCache() {
        adminService.evictCategoriesCache();
        return ResponseEntity.ok(ApiResponse.ok("Categories cache cleared", null));
    }
}
