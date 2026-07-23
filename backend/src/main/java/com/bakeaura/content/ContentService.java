package com.bakeaura.content;

import com.bakeaura.reel.Reel;
import com.bakeaura.reel.ReelService;
import com.bakeaura.review.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentService {

    // Score at most 200 reels in memory; prevents OOM on large datasets
    private static final int SCORING_POOL_LIMIT = 200;

    private final ReelService reelService;
    private final ReviewService reviewService;

    @Transactional(readOnly = true)
    public List<FeedItem> getRankedFeed(int page, int size, Long sellerId) {

        List<Reel> activeReels = (sellerId != null)
                ? reelService.getActiveReelsForSeller(sellerId, SCORING_POOL_LIMIT)
                : reelService.getAllActiveReels(SCORING_POOL_LIMIT);

        if (activeReels.isEmpty()) {
            return List.of();
        }

        Set<Long> sellerIds = activeReels.stream()
                .map(reel -> reel.getSeller().getId())
                .collect(Collectors.toSet());
        // Single GROUP BY query — replaces N per-seller lookups
        Map<Long, Double> sellerRatings = reviewService.getAverageRatings(sellerIds);

        // Use Math.max(..., 1) so the denominator is never 0 when all reels share
        // the same value (e.g. all 0 likes, or all uploaded in the same hour).
        long maxLikes = Math.max(activeReels.stream().mapToLong(Reel::getLikeCount).max().orElse(1L), 1L);
        long maxViews = Math.max(activeReels.stream().mapToLong(Reel::getViewCount).max().orElse(1L), 1L);
        LocalDateTime now = LocalDateTime.now();
        long maxAgeHours = Math.max(activeReels.stream()
                .mapToLong(reel -> Duration.between(reel.getCreatedAt(), now).toHours())
                .max()
                .orElse(1L), 1L);

        List<FeedItem> ranked = activeReels.stream()
                .map(reel -> {
                    long ageHours = Duration.between(reel.getCreatedAt(), now).toHours();
                    double normalizedRecency = 1.0 - ((double) ageHours / maxAgeHours);
                    double normalizedLikes = (double) reel.getLikeCount() / maxLikes;
                    double normalizedViews = (double) reel.getViewCount() / maxViews;
                    double ratingScore = sellerRatings.getOrDefault(reel.getSeller().getId(), 0.0) / 5.0;

                    double score = (normalizedRecency * 0.40)
                            + (normalizedLikes * 0.25)
                            + (normalizedViews * 0.20)
                            + (ratingScore * 0.15);

                    return new FeedItem(
                            reel.getId(),
                            reel.getVideoUrl(),
                            reel.getThumbnailUrl(),
                            reel.getCaption(),
                            reel.getSeller().getName(),
                            reel.getSeller().getId(),
                            reel.getLikeCount(),
                            reel.getViewCount(),
                            reel.getCommentCount(),
                            reel.getSaveCount(),
                            reel.getDurationSeconds(),
                            sellerRatings.getOrDefault(reel.getSeller().getId(), 0.0),
                            score
                    );
                })
                .sorted(Comparator.comparingDouble(FeedItem::getScore).reversed())
                .collect(Collectors.toList());

        int from = page * size;
        if (from >= ranked.size()) {
            return List.of();
        }
        return ranked.subList(from, Math.min(from + size, ranked.size()));
    }
}
