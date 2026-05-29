package com.bakeaura.reel;

import com.bakeaura.reel.ReelResponseDTO;
import com.bakeaura.reel.ReelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reels")
@RequiredArgsConstructor
public class ReelController {

    private final ReelService reelService;

    /**
     * Upload a new reel.
     *
     * Uses MediaType.MULTIPART_FORM_DATA_VALUE because we're sending
     * a file (binary data) along with text fields in the same request.
     * Regular JSON can't carry binary data — that's why multipart exists.
     *
     * BEGINNER NOTE: @RequestPart is like @RequestParam but works with
     * multipart/form-data. Use it instead of @RequestBody when uploading files.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER') or hasRole('INFLUENCER')")
    public ResponseEntity<ReelResponseDTO> uploadReel(
            @RequestPart("video") MultipartFile videoFile,
            @RequestPart("caption") String caption,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Validate: only allow video files
        String contentType = videoFile.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            return ResponseEntity.badRequest().build();
        }

        // Validate: max 200MB (roughly 60 seconds at decent quality)
        if (videoFile.getSize() > 200 * 1024 * 1024) {
            return ResponseEntity.badRequest().build();
        }

        // Step 1: Create the reel record immediately (returns PROCESSING status)
        ReelResponseDTO response = reelService.initiateUpload(caption, userDetails.getUsername());

        // Step 2: Start the Cloudinary upload in the background
        // This returns immediately — @Async handles the rest
        reelService.processVideoUpload(response.getId(), videoFile);

        // We return 202 Accepted (not 200 OK) because the work isn't done yet
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Get the scrollable feed — all active reels, newest first.
     * page=0&size=10 means "give me the first 10 reels"
     */
    @GetMapping("/feed")
    public ResponseEntity<List<ReelResponseDTO>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reelService.getActiveFeed(page, size));
    }

    /**
     * Get all reels by a specific seller (for their profile)
     */
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<List<ReelResponseDTO>> getSellerReels(@PathVariable Long sellerId) {
        return ResponseEntity.ok(reelService.getSellerReels(sellerId));
    }
}
