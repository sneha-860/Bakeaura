package com.bakeaura.review;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/api/sellers/{sellerId}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getSellerReviews(
            @PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reviews fetched",
                reviewService.getSellerReviews(sellerId, page, size)));
    }

    @GetMapping("/api/sellers/{sellerId}/reviews/summary")
    public ResponseEntity<ApiResponse<ReviewSummaryDto>> getSummary(
            @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Review summary fetched",
                reviewService.getSummary(sellerId)));
    }

    @PostMapping("/api/orders/{orderId}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReviewDto>> createReview(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody ReviewRequest request) {
        Long userId = Long.parseLong(authentication.getName());
        reviewService.createReview(userId, orderId, request);
        return ResponseEntity.ok(ApiResponse.ok(
                "Review saved",
                reviewService.getMyReview(userId, orderId)));
    }

    @GetMapping("/api/orders/{orderId}/reviews/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReviewDto>> getMyReview(
            Authentication authentication,
            @PathVariable Long orderId) {
        ReviewDto review = reviewService.getMyReview(Long.parseLong(authentication.getName()), orderId);
        return ResponseEntity.ok(ApiResponse.ok("Review fetched", review));
    }

    @DeleteMapping("/api/orders/{orderId}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            Authentication authentication,
            @PathVariable Long orderId) {
        reviewService.deleteReview(Long.parseLong(authentication.getName()), orderId);
        return ResponseEntity.ok(ApiResponse.ok("Review deleted", null));
    }

    @DeleteMapping("/api/admin/reviews/{reviewId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> adminDeleteReview(@PathVariable Long reviewId) {
        reviewService.adminDeleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.ok("Review removed by admin", null));
    }
}
