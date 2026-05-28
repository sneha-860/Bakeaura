package com.bakeaura.influencer;

import com.bakeaura.common.ApiResponse;
import com.bakeaura.enums.Role;
import com.bakeaura.user.UserDto;
import com.bakeaura.user.UserRepository;
import com.bakeaura.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/influencers")
@RequiredArgsConstructor
public class InfluencerController {
    private final UserRepository userRepository;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> getInfluencers() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Influencers fetched",
                userRepository.findByRoleAndIsActiveTrue(Role.INFLUENCER).stream().map(userService::toDto).toList()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getInfluencer(@PathVariable Long id) {
        return userRepository.findById(id)
                .filter(user -> user.getRole() == Role.INFLUENCER && Boolean.TRUE.equals(user.getIsActive()))
                .map(user -> ResponseEntity.ok(ApiResponse.ok("Influencer fetched", userService.toDto(user))))
                .orElse(ResponseEntity.notFound().build());
    }
}
