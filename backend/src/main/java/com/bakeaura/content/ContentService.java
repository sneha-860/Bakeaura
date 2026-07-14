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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final ReelService reelService;
    private final ReviewService reviewService;

    @Transactional(readOnly = true)
    public List<FeedItem> getRankedFeed() {

        List<Reel> activeReels = reelService.getAllActiveReels();

        if (activeReels.isEmpty()) {
            return List.of();
        }

        Map<Long, Double> sellerRatings = activeReels.stream()
                .map(reel -> reel.getSeller().getId())
                .distinct()
                .collect(Collectors.toMap(
                        sellerId -> sellerId,
                        sellerId -> {
                            Double avg = reviewService.getSummary(sellerId).getAverageRating();
                            return avg != null ? avg : 0.0;
                        }
                ));

        long maxLikes = activeReels.stream()
                .mapToLong(Reel::getLikeCount)
                .max()
                .orElse(1L);

        long maxViews = activeReels.stream()
                .mapToLong(Reel::getViewCount)
                .max()
                .orElse(1L);

        LocalDateTime now = LocalDateTime.now();
        long maxAgeHours = activeReels.stream()
                .mapToLong(reel -> Duration.between(reel.getCreatedAt(), now).toHours())
                .max()
                .orElse(1L);

        return activeReels.stream()
                .map(reel -> {
                    long ageHours = Duration.between(reel.getCreatedAt(), now).toHours();
                    double normalizedRecency = 1.0 - ((double) ageHours / maxAgeHours);
                    double normalizedLikes = (double) reel.getLikeCount() / maxLikes;
                    double normalizedViews = (double) reel.getViewCount() / maxViews;
                    double ratingScore = sellerRatings.getOrDefault(
                            reel.getSeller().getId(), 0.0) / 5.0;

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
                            sellerRatings.getOrDefault(reel.getSeller().getId(), 0.0),
                            score
                    );
                })
                .sorted(Comparator.comparingDouble(FeedItem::getScore).reversed())
                .collect(Collectors.toList());
    }
}
