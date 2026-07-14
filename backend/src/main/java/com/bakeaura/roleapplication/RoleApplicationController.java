package com.bakeaura.roleapplication;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/role-applications")
@RequiredArgsConstructor
public class RoleApplicationController {

    private final RoleApplicationService roleApplicationService;

    // Only CUSTOMER role can submit new applications — the service also enforces this.
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> apply(
            Authentication authentication,
            @Valid @RequestBody RoleApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Application submitted",
                roleApplicationService.apply(Long.parseLong(authentication.getName()), request)
        ));
    }

    // Any authenticated user can view their own application history — a SELLER or INFLUENCER
    // should still be able to see the application they submitted before being approved.
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RoleApplicationResponse>>> myApplications(
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Applications fetched",
                roleApplicationService.getMyApplications(Long.parseLong(authentication.getName()))
        ));
    }
}
