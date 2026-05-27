package com.bakeaura.roleapplication;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/role-applications")
@RequiredArgsConstructor
public class RoleApplicationController {

    private final RoleApplicationService roleApplicationService;

    @PostMapping
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> apply(
            Authentication authentication,
            @Valid @RequestBody RoleApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Application submitted",
                roleApplicationService.apply(authentication.getName(), request)
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<RoleApplicationResponse>>> myApplications(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Applications fetched",
                roleApplicationService.getMyApplications(authentication.getName())
        ));
    }
}
