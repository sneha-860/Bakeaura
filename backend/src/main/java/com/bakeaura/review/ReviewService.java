package com.bakeaura.review;

import com.bakeaura.enums.OrderStatus;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.order.Order;
import com.bakeaura.order.OrderService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderService orderService;
    private final UserRepository userRepository;

    public List<ReviewDto> getSellerReviews(Long sellerId, int page, int size) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        return reviewRepository.findBySellerOrderByCreatedAtDesc(seller, PageRequest.of(page, size))
                .stream()
                .map(this::toDto)
                .toList();
    }

    // MAJ-08: replaces N per-seller getSummary() calls in feed ranking with one GROUP BY query
    public Map<Long, Double> getAverageRatings(Set<Long> sellerIds) {
        if (sellerIds.isEmpty()) return Map.of();
        return reviewRepository.averageRatingsBatch(sellerIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).doubleValue()
                ));
    }

    // MIN-09: batch avg+count in one GROUP BY query — used by seller listings to avoid N+1
    public Map<Long, ReviewSummaryDto> getSummaryBatch(Set<Long> sellerIds) {
        if (sellerIds.isEmpty()) return Map.of();
        return reviewRepository.summaryBatch(sellerIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> new ReviewSummaryDto(
                                (Long) row[0],
                                ((Number) row[1]).doubleValue(),
                                (Long) row[2]
                        )
                ));
    }

    // MAJ-23: targeted lookup — avoids fetching all seller reviews to find one
    @Transactional(readOnly = true)
    public ReviewDto getMyReview(Long userId, Long orderId) {
        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Order order = orderService.getOrderEntityById(orderId);
        return reviewRepository.findByCustomerAndOrder(customer, order)
                .map(this::toDto)
                .orElse(null);
    }

    @Cacheable(value = "reviewSummaries", key = "#sellerId")
    public ReviewSummaryDto getSummary(Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        return new ReviewSummaryDto(
                sellerId,
                reviewRepository.averageRatingForSeller(seller),
                reviewRepository.countBySeller(seller));
    }

    @Transactional
    @CacheEvict(value = "reviewSummaries", key = "#result")
    public Long createReview(Long userId, Long orderId, ReviewRequest request) {
        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderService.getOrderEntityById(orderId);

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new BadRequestException("You can only review your own orders");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("You can only review an order after it has been delivered");
        }

        if (reviewRepository.existsByCustomerAndOrder(customer, order)) {
            throw new BadRequestException("You have already reviewed this order");
        }

        Review review = new Review();
        review.setCustomer(customer);
        review.setSeller(order.getSeller());
        review.setOrder(order);
        review.setRating(request.getRating());
        review.setComment(request.getComment());

        reviewRepository.save(review);
        return order.getSeller().getId();
    }

    @Transactional
    @CacheEvict(value = "reviewSummaries", key = "#result")
    public Long deleteReview(Long userId, Long orderId) {
        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderService.getOrderEntityById(orderId);

        Review review = reviewRepository.findByCustomerAndOrder(customer, order)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        Long sellerId = review.getSeller().getId();
        reviewRepository.delete(review);
        return sellerId;
    }

    @Transactional
    @CacheEvict(value = "reviewSummaries", key = "#result")
    public Long adminDeleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        Long sellerId = review.getSeller().getId();
        reviewRepository.delete(review);
        return sellerId;
    }

    private ReviewDto toDto(Review review) {
        return new ReviewDto(
                review.getId(),
                review.getSeller().getId(),
                anonymise(review.getCustomer().getName()),
                review.getOrder().getId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }

    // Returns "First L." to identify the reviewer without exposing their full name
    private static String anonymise(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Customer";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        return parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
    }
}
