package com.bakeaura.review;

import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.product.Product;
import com.bakeaura.product.ProductRepository;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public List<ReviewDto> getProductReviews(Long productId) {
        Product product = getProduct(productId);
        return reviewRepository.findByProductOrderByCreatedAtDesc(product).stream()
                .map(this::toDto)
                .toList();
    }

    public ReviewSummaryDto getSummary(Long productId) {
        Product product = getProduct(productId);
        return new ReviewSummaryDto(productId, reviewRepository.averageRatingForProduct(product), reviewRepository.countByProduct(product));
    }

    @Transactional
    public ReviewDto upsertReview(String email, Long productId, ReviewRequest request) {
        User user = getUser(email);
        Product product = getProduct(productId);
        Review review = reviewRepository.findByUserAndProduct(user, product).orElseGet(Review::new);
        review.setUser(user);
        review.setProduct(product);
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        return toDto(reviewRepository.save(review));
    }

    @Transactional
    public void deleteReview(String email, Long productId) {
        User user = getUser(email);
        Product product = getProduct(productId);
        reviewRepository.findByUserAndProduct(user, product).ifPresent(reviewRepository::delete);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private ReviewDto toDto(Review review) {
        return new ReviewDto(
                review.getId(),
                review.getProduct().getId(),
                review.getUser().getId(),
                review.getUser().getName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
