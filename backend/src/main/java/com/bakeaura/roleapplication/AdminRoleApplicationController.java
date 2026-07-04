package com.bakeaura.roleapplication;

import com.bakeaura.common.ApiResponse;
import com.bakeaura.enums.ApplicationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/role-applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleApplicationController {

    private final RoleApplicationService roleApplicationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleApplicationResponse>>> getApplications(
            @RequestParam(required = false) ApplicationStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Applications fetched",
                roleApplicationService.getApplications(status)
        ));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> approve(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody RoleApplicationReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Application approved",
                roleApplicationService.approve(id, Long.parseLong(authentication.getName()), request)
        ));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> reject(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody RoleApplicationReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Application rejected",
                roleApplicationService.reject(id, Long.parseLong(authentication.getName()), request)
        ));
    }
}
