package com.bakeaura.reel;



import com.bakeaura.cloudinary.CloudinaryService;
import com.bakeaura.reel.ReelResponseDTO;
import com.bakeaura.reel.Reel;
import com.bakeaura.user.User;
import com.bakeaura.reel.ReelRepository;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReelService {

    private final ReelRepository reelRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket — you already have this!

    /**
     * Step 1: Save a "PROCESSING" reel immediately so we can return its ID to the frontend.
     * The actual Cloudinary upload happens asynchronously.
     */
    public ReelResponseDTO initiateUpload(String caption, String sellerEmail) {
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        Reel reel = new Reel();
        reel.setSeller(seller);
        reel.setCaption(caption);
        reel.setStatus(Reel.ReelStatus.PROCESSING);
        Reel saved = reelRepository.save(reel);

        log.info("Created PROCESSING reel ID: {} for seller: {}", saved.getId(), sellerEmail);
        return toResponseDTO(saved);
    }

    /**
     * Step 2: This runs in a BACKGROUND THREAD because of @Async.
     * Spring Boot will not wait for this to finish before returning the HTTP response.
     *
     * BEGINNER NOTE: @Async only works when the method is called from OUTSIDE
     * this class (i.e., from a Controller or another Service). It does NOT work
     * if you call it from within the same class — that's a very common mistake!
     */
    @Async
    public void processVideoUpload(Long reelId, MultipartFile videoFile) {
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new RuntimeException("Reel not found: " + reelId));

        try {
            log.info("Starting async Cloudinary upload for reel ID: {}", reelId);

            Map<String, Object> result = cloudinaryService.uploadVideo(
                    videoFile,
                    "bakeaura/reels"
            );

            // Extract values from Cloudinary's response map
            reel.setVideoUrl((String) result.get("secure_url"));
            reel.setCloudinaryPublicId((String) result.get("public_id"));

            // Duration comes back as a Double from Cloudinary (e.g., 45.3 seconds)
            Object durationObj = result.get("duration");
            if (durationObj != null) {
                reel.setDurationSeconds(((Double) durationObj).intValue());
            }

            // The "eager" transformation we set up generates a thumbnail
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> eager = (List<Map<String, Object>>) result.get("eager");
            if (eager != null && !eager.isEmpty()) {
                reel.setThumbnailUrl((String) eager.get(0).get("secure_url"));
            }

            reel.setStatus(Reel.ReelStatus.ACTIVE);
            reelRepository.save(reel);

            log.info("Reel ID: {} is now ACTIVE. URL: {}", reelId, reel.getVideoUrl());

            // Notify frontend via WebSocket that the upload is done
            // The frontend will be subscribed to /topic/reels/{sellerId}
            messagingTemplate.convertAndSend(
                    "/topic/reels/" + reel.getSeller().getId(),
                    toResponseDTO(reel)
            );

        } catch (Exception e) {
            log.error("Failed to process reel ID: {}", reelId, e);
            reel.setStatus(Reel.ReelStatus.FAILED);
            reelRepository.save(reel);

            // Notify frontend of failure too
            messagingTemplate.convertAndSend(
                    "/topic/reels/" + reel.getSeller().getId(),
                    Map.of("reelId", reelId, "status", "FAILED", "error", e.getMessage())
            );
        }
    }

    /**
     * Returns all ACTIVE reels for the feed, newest first.
     * Phase 6 will replace this with the ranking algorithm.
     */
    public List<ReelResponseDTO> getActiveFeed(int page, int size) {
        return reelRepository
                .findByStatusOrderByCreatedAtDesc(Reel.ReelStatus.ACTIVE)
                .stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get reels by a specific seller (for their profile page)
     */
    public List<ReelResponseDTO> getSellerReels(Long sellerId) {
        return reelRepository
                .findBySellerIdAndStatusOrderByCreatedAtDesc(sellerId, Reel.ReelStatus.ACTIVE)
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    private ReelResponseDTO toResponseDTO(Reel reel) {
        ReelResponseDTO dto = new ReelResponseDTO();
        dto.setId(reel.getId());
        dto.setCaption(reel.getCaption());
        dto.setVideoUrl(reel.getVideoUrl());
        dto.setThumbnailUrl(reel.getThumbnailUrl());
        dto.setDurationSeconds(reel.getDurationSeconds());
        dto.setStatus(reel.getStatus().name());
        dto.setLikeCount(reel.getLikeCount());
        dto.setCommentCount(reel.getCommentCount());
        dto.setSaveCount(reel.getSaveCount());
        dto.setViewCount(reel.getViewCount());
        dto.setCreatedAt(reel.getCreatedAt());
        if (reel.getSeller() != null) {
            dto.setSellerId(reel.getSeller().getId());
            dto.setSellerName(reel.getSeller().getName());
        }
        return dto;
    }
}