package com.bakeaura.reel;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/reels")
@RequiredArgsConstructor
public class ReelController {

    private final ReelService reelService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER') or hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<ReelResponseDTO>> uploadReel(
            @RequestPart("video") MultipartFile videoFile,
            @RequestPart("caption") @Size(max = 500, message = "Caption must be 500 characters or fewer") String caption,
            Authentication authentication) {

        String contentType = videoFile.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Only video files are allowed", "INVALID_FILE_TYPE"));
        }

        if (videoFile.getSize() > 200 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File size exceeds the 200 MB limit", "FILE_TOO_LARGE"));
        }

        Long userId = Long.parseLong(authentication.getName());
        ReelResponseDTO response = reelService.initiateUpload(caption, userId);
        reelService.processVideoUpload(response.getId(), videoFile);
        return ResponseEntity.accepted().body(ApiResponse.ok("Reel upload started", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SELLER') or hasRole('INFLUENCER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteReel(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        reelService.deleteReel(id, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Reel deleted", null));
    }

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<Page<ReelResponseDTO>>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Feed fetched", reelService.getActiveFeed(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReelResponseDTO>> getReel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Reel fetched", reelService.getReelById(id)));
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<ApiResponse<List<ReelResponseDTO>>> getSellerReels(
            @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok("Seller reels fetched",
                reelService.getSellerReels(sellerId)));
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<Void> incrementViewCount(@PathVariable Long id, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        reelService.incrementViewCount(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    @PreAuthorize("hasAnyRole('CUSTOMER','SELLER','INFLUENCER')")
    public ResponseEntity<Void> likeReel(@PathVariable Long id, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        reelService.likeReel(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/like")
    @PreAuthorize("hasAnyRole('CUSTOMER','SELLER','INFLUENCER')")
    public ResponseEntity<Void> unlikeReel(@PathVariable Long id, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        reelService.unlikeReel(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/save")
    @PreAuthorize("hasAnyRole('CUSTOMER','SELLER','INFLUENCER')")
    public ResponseEntity<Void> saveReel(@PathVariable Long id, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        reelService.saveReel(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/save")
    @PreAuthorize("hasAnyRole('CUSTOMER','SELLER','INFLUENCER')")
    public ResponseEntity<Void> unsaveReel(@PathVariable Long id, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        reelService.unsaveReel(id, userId);
        return ResponseEntity.noContent().build();
    }
}
