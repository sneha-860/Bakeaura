package com.bakeaura.review;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getProductReviews(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Reviews fetched", reviewService.getProductReviews(productId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReviewSummaryDto>> getSummary(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Review summary fetched", reviewService.getSummary(productId)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<ReviewDto>> upsertReview(
            Authentication authentication,
            @PathVariable Long productId,
            @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Review saved", reviewService.upsertReview(authentication.getName(), productId, request)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteReview(Authentication authentication, @PathVariable Long productId) {
        reviewService.deleteReview(authentication.getName(), productId);
        return ResponseEntity.ok(ApiResponse.ok("Review deleted", null));
    }
}
