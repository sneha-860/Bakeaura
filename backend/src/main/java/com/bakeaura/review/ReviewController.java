package com.bakeaura.review;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/api/sellers/{sellerId}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getSellerReviews(
            @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reviews fetched",
                reviewService.getSellerReviews(sellerId)));
    }

    @GetMapping("/api/sellers/{sellerId}/reviews/summary")
    public ResponseEntity<ApiResponse<ReviewSummaryDto>> getSummary(
            @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Review summary fetched",
                reviewService.getSummary(sellerId)));
    }

    @PostMapping("/api/orders/{orderId}/reviews")
    public ResponseEntity<ApiResponse<ReviewDto>> createReview(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Review saved",
                reviewService.createReview(Long.parseLong(authentication.getName()), orderId, request)));
    }

    @DeleteMapping("/api/orders/{orderId}/reviews")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            Authentication authentication,
            @PathVariable Long orderId) {
        reviewService.deleteReview(Long.parseLong(authentication.getName()), orderId);
        return ResponseEntity.ok(ApiResponse.ok("Review deleted", null));
    }
}