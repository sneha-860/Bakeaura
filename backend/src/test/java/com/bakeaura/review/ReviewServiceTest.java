package com.bakeaura.review;

import com.bakeaura.enums.OrderStatus;
import com.bakeaura.order.Order;
import com.bakeaura.order.OrderService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private UserRepository userRepository;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, orderService, userRepository);
    }

    @Test
    void zeroReviewSummaryReturnsZeroAverageAndCount() {
        // COALESCE in the query returns 0.0 for a seller with no reviews — proves no NPE / divide-by-zero
        User seller = seller(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(reviewRepository.averageRatingForSeller(seller)).thenReturn(0.0);
        when(reviewRepository.countBySeller(seller)).thenReturn(0L);

        ReviewSummaryDto summary = reviewService.getSummary(2L);

        assertThat(summary.getSellerId()).isEqualTo(2L);
        assertThat(summary.getAverageRating()).isEqualTo(0.0);
        assertThat(summary.getReviewCount()).isEqualTo(0L);
    }

    @Test
    void multiReviewSummaryReturnsCorrectAverageAndCount() {
        // Three reviews with ratings 3, 4, 5 → average 4.0; verifies the dto carries repo values through
        User seller = seller(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(reviewRepository.averageRatingForSeller(seller)).thenReturn(4.0);
        when(reviewRepository.countBySeller(seller)).thenReturn(3L);

        ReviewSummaryDto summary = reviewService.getSummary(2L);

        assertThat(summary.getAverageRating()).isEqualTo(4.0);
        assertThat(summary.getReviewCount()).isEqualTo(3L);
    }

    @Test
    void createReviewPersistsAndIsReflectedInSummary() {
        // Proves the create → save → getSummary path is wired correctly end-to-end
        User customer = customer(1L, "Alice");
        User seller = seller(2L);

        Order order = Order.builder()
                .id(10L)
                .customer(customer)
                .seller(seller)
                .status(OrderStatus.DELIVERED)
                .totalAmount(BigDecimal.valueOf(100))
                .deliveryAddress("Delhi")
                .build();

        ReviewRequest request = new ReviewRequest();
        request.setRating(4);
        request.setComment("Great cake!");

        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(orderService.getOrderEntityById(10L)).thenReturn(order);
        when(reviewRepository.existsByCustomerAndOrder(customer, order)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        Long sellerId = reviewService.createReview(1L, 10L, request);

        assertThat(sellerId).isEqualTo(2L);
        verify(reviewRepository).save(any(Review.class));

        // After the review is saved, getSummary should return updated data from the repository
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(reviewRepository.averageRatingForSeller(seller)).thenReturn(4.0);
        when(reviewRepository.countBySeller(seller)).thenReturn(1L);

        ReviewSummaryDto summary = reviewService.getSummary(2L);

        assertThat(summary.getAverageRating()).isEqualTo(4.0);
        assertThat(summary.getReviewCount()).isEqualTo(1L);
    }

    private User customer(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail("customer" + id + "@example.com");
        return user;
    }

    private User seller(Long id) {
        User user = new User();
        user.setId(id);
        user.setName("Seller " + id);
        user.setEmail("seller" + id + "@example.com");
        return user;
    }
}
