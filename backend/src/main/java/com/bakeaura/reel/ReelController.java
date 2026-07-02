package com.bakeaura.reel;

import com.bakeaura.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/reels")
@RequiredArgsConstructor
public class ReelController {

    private final ReelService reelService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER') or hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<ReelResponseDTO>> uploadReel(
            @RequestPart("video") MultipartFile videoFile,
            @RequestPart("caption") String caption,
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

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<Page<ReelResponseDTO>>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Feed fetched", reelService.getActiveFeed(page, size)));
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<ApiResponse<List<ReelResponseDTO>>> getSellerReels(
            @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok("Seller reels fetched",
                reelService.getSellerReels(sellerId)));
    }
}
