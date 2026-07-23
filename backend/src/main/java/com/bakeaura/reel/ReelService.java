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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReelService {

    private final ReelRepository reelRepository;
    private final ReelLikeRepository reelLikeRepository;
    private final ReelSaveRepository reelSaveRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

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
        // JOIN FETCH loads the seller eagerly so seller.getName() is available in memory
        // after the JPA session closes. findById() (lazy) would throw LazyInitializationException
        // when toResponseDTO() accesses seller.getName() later in this async thread.
        Reel reel = reelRepository.findByIdWithSeller(reelId)
                .orElseThrow(() -> new RuntimeException("Reel not found: " + reelId));

        // Capture seller ID before any try/catch — always safe, ID is on the FK column.
        Long sellerId = reel.getSeller().getId();

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

            messagingTemplate.convertAndSend("/topic/reels/" + sellerId, toResponseDTO(reel));

        } catch (Exception e) {
            log.error("Failed to process reel ID: {}", reelId, e);
            reel.setStatus(Reel.ReelStatus.FAILED);
            reelRepository.save(reel);

            // Same DTO format as the success case — frontend checks updatedReel.id and
            // updatedReel.status. The old Map.of("reelId", ...) used the wrong key and
            // was silently ignored by the frontend listener.
            messagingTemplate.convertAndSend("/topic/reels/" + sellerId, toResponseDTO(reel));
        }
    }

    @Transactional
    public void deleteReel(Long reelId, Long requestingUserId, boolean isAdmin) {
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel not found"));

        if (!isAdmin && !reel.getSeller().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("You can only delete your own reels");
        }

        String publicId = reel.getCloudinaryPublicId();
        if (publicId != null && !publicId.isBlank()) {
            try {
                cloudinaryService.deleteFile(publicId, "video");
            } catch (IOException e) {
                log.warn("Cloudinary delete failed for reel {} (public_id: {}); continuing with DB delete. Cause: {}",
                        reelId, publicId, e.getMessage());
            }
        }

        // Delete interaction rows before the reel — reel_likes and reel_saves store
        // reel_id as a plain Long (no FK), so there is no DB cascade to rely on.
        reelLikeRepository.deleteAllByReelId(reelId);
        reelSaveRepository.deleteAllByReelId(reelId);
        reelRepository.deleteById(reelId);
        log.info("Deleted reel ID: {} by seller ID: {}", reelId, requestingUserId);
    }

    public Page<ReelResponseDTO> getActiveFeed(int page, int size) {
        return reelRepository
                .findByStatusOrderByCreatedAtDesc(
                        Reel.ReelStatus.ACTIVE,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponseDTO);
    }

    @Transactional
    public void incrementViewCount(Long reelId, Long userId) {
        String dedupKey = "reel:view:" + reelId + ":" + userId;
        Boolean alreadyViewed = redisTemplate.hasKey(dedupKey);
        if (Boolean.TRUE.equals(alreadyViewed)) {
            return;
        }
        reelRepository.incrementViewCount(reelId);
        redisTemplate.opsForValue().set(dedupKey, "1", Duration.ofHours(24));
    }

    @Transactional
    public void likeReel(Long reelId, Long userId) {
        if (!reelLikeRepository.existsByReelIdAndUserId(reelId, userId)) {
            ReelLike like = new ReelLike();
            like.setReelId(reelId);
            like.setUserId(userId);
            reelLikeRepository.save(like);
            reelRepository.incrementLikeCount(reelId);
        }
    }

    @Transactional
    public void saveReel(Long reelId, Long userId) {
        if (!reelSaveRepository.existsByReelIdAndUserId(reelId, userId)) {
            ReelSave save = new ReelSave();
            save.setReelId(reelId);
            save.setUserId(userId);
            reelSaveRepository.save(save);
            reelRepository.incrementSaveCount(reelId);
        }
    }

    @Transactional
    public void unlikeReel(Long reelId, Long userId) {
        if (reelLikeRepository.existsByReelIdAndUserId(reelId, userId)) {
            reelLikeRepository.deleteByReelIdAndUserId(reelId, userId);
            reelRepository.decrementLikeCount(reelId);
        }
    }

    @Transactional
    public void unsaveReel(Long reelId, Long userId) {
        if (reelSaveRepository.existsByReelIdAndUserId(reelId, userId)) {
            reelSaveRepository.deleteByReelIdAndUserId(reelId, userId);
            reelRepository.decrementSaveCount(reelId);
        }
    }

    public List<Reel> getAllActiveReels(int limit) {
        return reelRepository.findAllActive(PageRequest.of(0, limit));
    }

    public List<Reel> getActiveReelsForSeller(Long sellerId, int limit) {
        return reelRepository.findActiveBySellerIdWithLimit(sellerId, PageRequest.of(0, limit));
    }

    public ReelResponseDTO getReelById(Long reelId) {
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel not found"));
        return toResponseDTO(reel);
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
