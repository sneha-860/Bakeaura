package com.bakeaura.review;

import com.bakeaura.enums.OrderStatus;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.order.Order;
import com.bakeaura.order.OrderRepository;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public List<ReviewDto> getSellerReviews(Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        return reviewRepository.findBySellerOrderByCreatedAtDesc(seller).stream()
                .map(this::toDto)
                .toList();
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
    @CacheEvict(value = "reviewSummaries", key = "#result.sellerId")
    public ReviewDto createReview(Long userId, Long orderId, ReviewRequest request) {
        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

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

        return toDto(reviewRepository.save(review));
    }

    @Transactional
    @CacheEvict(value = "reviewSummaries", key = "#result")
    public Long deleteReview(Long userId, Long orderId) {
        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        Review review = reviewRepository.findByCustomerAndOrder(customer, order)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        Long sellerId = review.getSeller().getId();
        reviewRepository.delete(review);
        return sellerId;
    }

    private ReviewDto toDto(Review review) {
        return new ReviewDto(
                review.getId(),
                review.getSeller().getId(),
                review.getCustomer().getId(),
                review.getCustomer().getName(),
                review.getOrder().getId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}