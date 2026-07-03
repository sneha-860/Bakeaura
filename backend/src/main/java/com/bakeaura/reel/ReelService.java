package com.bakeaura.reel;

import com.bakeaura.cloudinary.CloudinaryService;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final SimpMessagingTemplate messagingTemplate;

    public ReelResponseDTO initiateUpload(String caption, Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));

        Reel reel = new Reel();
        reel.setSeller(seller);
        reel.setCaption(caption);
        reel.setStatus(Reel.ReelStatus.PROCESSING);
        Reel saved = reelRepository.save(reel);

        log.info("Created PROCESSING reel ID: {} for seller ID: {}", saved.getId(), sellerId);
        return toResponseDTO(saved);
    }

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

            reel.setVideoUrl((String) result.get("secure_url"));
            reel.setCloudinaryPublicId((String) result.get("public_id"));

            Object durationObj = result.get("duration");
            if (durationObj != null) {
                reel.setDurationSeconds(((Double) durationObj).intValue());
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> eager = (List<Map<String, Object>>) result.get("eager");
            if (eager != null && !eager.isEmpty()) {
                reel.setThumbnailUrl((String) eager.get(0).get("secure_url"));
            }

            reel.setStatus(Reel.ReelStatus.ACTIVE);
            reelRepository.save(reel);

            log.info("Reel ID: {} is now ACTIVE. URL: {}", reelId, reel.getVideoUrl());

            messagingTemplate.convertAndSend(
                    "/topic/reels/" + reel.getSeller().getId(),
                    toResponseDTO(reel)
            );

        } catch (Exception e) {
            log.error("Failed to process reel ID: {}", reelId, e);
            reel.setStatus(Reel.ReelStatus.FAILED);
            reelRepository.save(reel);

            messagingTemplate.convertAndSend(
                    "/topic/reels/" + reel.getSeller().getId(),
                    Map.of("reelId", reelId, "status", "FAILED", "error", e.getMessage())
            );
        }
    }

    public Page<ReelResponseDTO> getActiveFeed(int page, int size) {
        return reelRepository
                .findByStatusOrderByCreatedAtDesc(
                        Reel.ReelStatus.ACTIVE,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponseDTO);
    }

    @Transactional
    public void incrementViewCount(Long reelId) {
        reelRepository.incrementViewCount(reelId);
    }

    public List<ReelResponseDTO> getSellerReels(Long sellerId) {
        return reelRepository
                .findBySeller_IdAndStatusOrderByCreatedAtDesc(sellerId, Reel.ReelStatus.ACTIVE)
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