package com.bakeaura.seller;

import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.bakeaura.map.MapService;
import com.bakeaura.product.ProductService;
import com.bakeaura.review.ReviewService;
import com.bakeaura.review.ReviewSummaryDto;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bakeaura.order.OrderService;
import com.bakeaura.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerService {

    private final UserRepository userRepository;
    private final ProductService productService;
    private final SellerProfileRepository sellerProfileRepository;
    private final MapService mapService;
    private final ReviewService reviewService;
    private final OrderService orderService;

    @Cacheable("sellerList")
    public List<SellerProfileDto> getSellers() {
        List<User> allSellers = userRepository.findByRoleAndIsActiveTrue(Role.SELLER);
        if (allSellers.isEmpty()) return List.of();

        Set<Long> ids = allSellers.stream().map(User::getId).collect(Collectors.toSet());

        Map<Long, SellerProfile> profileMap = sellerProfileRepository.findAllByUserIdIn(ids)
                .stream().collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        Map<Long, Long> productCountMap = productService.countProductsBySellerIds(ids);

        List<User> visible = allSellers.stream()
                .filter(s -> {
                    SellerProfile p = profileMap.get(s.getId());
                    return p != null
                            && p.getShopName() != null && !p.getShopName().isBlank()
                            && Boolean.TRUE.equals(p.getIsOpen())
                            && productCountMap.getOrDefault(s.getId(), 0L) > 0;
                })
                .toList();

        if (visible.isEmpty()) return List.of();

        Set<Long> visibleIds = visible.stream().map(User::getId).collect(Collectors.toSet());
        Map<Long, ReviewSummaryDto> reviewMap = reviewService.getSummaryBatch(visibleIds);

        return visible.stream()
                .map(s -> toDtoBatch(s, profileMap.get(s.getId()),
                        productCountMap.getOrDefault(s.getId(), 0L),
                        reviewMap.get(s.getId())))
                .toList();
    }

    @Cacheable(value = "sellerProfiles", key = "#id")
    public SellerProfileDto getSeller(Long id) {
        User seller = userRepository.findById(id)
                .filter(user -> user.getRole() == Role.SELLER)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        return toDto(seller);
    }

    public List<SellerProfileDto> getNearbySellers(double latitude, double longitude, double radius) {
        List<User> allSellers = userRepository.findByRoleAndIsActiveTrue(Role.SELLER);
        if (allSellers.isEmpty()) return List.of();

        Set<Long> ids = allSellers.stream().map(User::getId).collect(Collectors.toSet());
        Map<Long, SellerProfile> profileMap = sellerProfileRepository.findAllByUserIdIn(ids)
                .stream().collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));
        Map<Long, Long> productCountMap = productService.countProductsBySellerIds(ids);

        List<User> visible = allSellers.stream()
                .filter(s -> {
                    SellerProfile p = profileMap.get(s.getId());
                    return p != null
                            && p.getShopName() != null && !p.getShopName().isBlank()
                            && Boolean.TRUE.equals(p.getIsOpen())
                            && productCountMap.getOrDefault(s.getId(), 0L) > 0
                            && s.getLatitude() != null && s.getLongitude() != null
                            && mapService.calculateDistance(latitude, longitude,
                                    s.getLatitude(), s.getLongitude()) <= radius;
                })
                .toList();

        if (visible.isEmpty()) return List.of();

        Set<Long> visibleIds = visible.stream().map(User::getId).collect(Collectors.toSet());
        Map<Long, ReviewSummaryDto> reviewMap = reviewService.getSummaryBatch(visibleIds);

        return visible.stream()
                .map(s -> toDtoBatch(s, profileMap.get(s.getId()),
                        productCountMap.getOrDefault(s.getId(), 0L),
                        reviewMap.get(s.getId())))
                .toList();
    }

    @Transactional
    @CacheEvict(value = {"sellerProfiles", "sellerList"}, allEntries = true)
    public SellerProfileDto updateProfile(Long userId, UpdateSellerProfileDto request) {
        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (seller.getRole() != Role.SELLER) {
            throw new AccessDeniedException("User is not a seller");
        }

        SellerProfile profile = sellerProfileRepository.findByUserId(userId)
                .orElseGet(() -> SellerProfile.builder().user(seller).build());

        if (request.getShopName() != null) profile.setShopName(request.getShopName());
        if (request.getShopBio() != null) profile.setShopBio(request.getShopBio());
        if (request.getDeliveryRadiusKm() != null) profile.setDeliveryRadiusKm(request.getDeliveryRadiusKm());
        if (request.getBannerImageUrl() != null) profile.setBannerImageUrl(request.getBannerImageUrl());

        sellerProfileRepository.save(profile);
        return toDto(seller);
    }

    @Transactional
    @CacheEvict(value = {"sellerProfiles", "sellerList"}, allEntries = true)
    public SellerProfileDto toggleOpen(Long userId) {
        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (seller.getRole() != Role.SELLER) {
            throw new AccessDeniedException("User is not a seller");
        }

        SellerProfile profile = sellerProfileRepository.findByUserId(userId)
                .orElseGet(() -> SellerProfile.builder().user(seller).isOpen(false).build());

        // Only enforce readiness when opening — closing is always allowed
        if (!Boolean.TRUE.equals(profile.getIsOpen())) {
            if (profile.getShopName() == null || profile.getShopName().isBlank()) {
                throw new BadRequestException("Set your shop name before opening for business");
            }
            if (profile.getDeliveryRadiusKm() == null) {
                throw new BadRequestException("Set your delivery radius before opening for business");
            }
            if (seller.getLatitude() == null || seller.getLongitude() == null) {
                throw new BadRequestException("Set your shop location before opening for business — customers need it for distance and delivery calculations");
            }
            if (productService.countProductsBySeller(userId) == 0) {
                throw new BadRequestException("Add at least one product before opening for business");
            }
        }

        profile.setIsOpen(!profile.getIsOpen());
        sellerProfileRepository.save(profile);
        return toDto(seller);
    }

    @Transactional
    @CacheEvict(value = {"sellerProfiles", "sellerList"}, allEntries = true)
    public void createProfileForNewSeller(User user) {
        if (!sellerProfileRepository.existsByUserId(user.getId())) {
            SellerProfile profile = SellerProfile.builder()
                    .user(user)
                    .isOpen(false)
                    .totalRatings(0)
                    .averageRating(0.0)
                    .build();
            sellerProfileRepository.save(profile);
        }
    }

    public Double getDeliveryRadiusKm(Long sellerId) {
        return sellerProfileRepository.findByUserId(sellerId)
                .map(SellerProfile::getDeliveryRadiusKm)
                .orElse(null);
    }

    @Transactional
    @CacheEvict(value = {"sellerProfiles", "sellerList"}, allEntries = true)
    public void closeShopOnDemotion(Long userId) {
        sellerProfileRepository.findByUserId(userId).ifPresent(profile -> {
            profile.setIsOpen(false);
            sellerProfileRepository.save(profile);
        });
    }

    private SellerProfileDto toDto(User seller) {
        SellerProfile profile = sellerProfileRepository.findByUserId(seller.getId()).orElse(null);
        long productCount = productService.countProductsBySeller(seller.getId());
        ReviewSummaryDto reviewSummary = reviewService.getSummary(seller.getId());
        return toDtoBatch(seller, profile, productCount, reviewSummary);
    }

    private SellerProfileDto toDtoBatch(User seller, SellerProfile profile, long productCount, ReviewSummaryDto reviewSummary) {
        int completeness = 0;
        if (profile != null) {
            if (profile.getShopName() != null && !profile.getShopName().isBlank()) completeness += 20;
            if (profile.getShopBio() != null && !profile.getShopBio().isBlank()) completeness += 20;
            if (profile.getBannerImageUrl() != null && !profile.getBannerImageUrl().isBlank()) completeness += 20;
            if (profile.getDeliveryRadiusKm() != null) completeness += 20;
        }
        if (productCount > 0) completeness += 20;

        return SellerProfileDto.builder()
                .id(seller.getId())
                .name(seller.getName())
                .shopName(profile != null ? profile.getShopName() : null)
                .shopBio(profile != null ? profile.getShopBio() : null)
                .deliveryRadiusKm(profile != null ? profile.getDeliveryRadiusKm() : null)
                .isOpen(profile != null ? profile.getIsOpen() : false)
                .bannerImageUrl(profile != null ? profile.getBannerImageUrl() : null)
                .totalRatings(reviewSummary != null && reviewSummary.getReviewCount() != null ? reviewSummary.getReviewCount().intValue() : 0)
                .averageRating(reviewSummary != null && reviewSummary.getAverageRating() != null ? reviewSummary.getAverageRating() : 0.0)
                .productCount(productCount)
                .profileCompleteness(completeness)
                .build();
    }

    public SellerAnalyticsDto getDashboardAnalytics(Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (seller.getRole() != Role.SELLER) {
            throw new AccessDeniedException("User is not a seller");
        }

        LocalDate today = LocalDate.now();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1).atStartOfDay();

        BigDecimal totalRevenueAllTime = orderService.getSellerRevenueAllTime(sellerId);

        BigDecimal totalRevenueThisMonth = orderService.getSellerRevenueSince(sellerId, startOfMonth);

        BigDecimal totalRevenueThisWeek = orderService.getSellerRevenueSince(sellerId, startOfWeek);

        Map<String, Long> orderCountsByStatus = orderService
                .getOrderCountsByStatusForSeller(sellerId)
                .stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]
                ));

        long revenueOrderCount = orderCountsByStatus.entrySet().stream()
                .filter(e -> !e.getKey().equals(OrderStatus.PENDING.name())
                        && !e.getKey().equals(OrderStatus.CANCELLED.name()))
                .mapToLong(Map.Entry::getValue)
                .sum();

        BigDecimal averageOrderValue = revenueOrderCount == 0
                ? BigDecimal.ZERO
                : totalRevenueAllTime.divide(BigDecimal.valueOf(revenueOrderCount), 2, RoundingMode.HALF_UP);

        Page<Object[]> bestSellerPage = orderService.getBestSellingProductsBySeller(
                sellerId, PageRequest.of(0, 1));
        String bestSellingProductName = null;
        Long bestSellingProductQuantity = 0L;
        if (!bestSellerPage.isEmpty()) {
            Object[] top = bestSellerPage.getContent().get(0);
            bestSellingProductName = (String) top[0];
            bestSellingProductQuantity = (Long) top[1];
        }

        ReviewSummaryDto reviewSummary = reviewService.getSummary(sellerId);

        return SellerAnalyticsDto.builder()
                .totalRevenueAllTime(totalRevenueAllTime)
                .totalRevenueThisMonth(totalRevenueThisMonth)
                .totalRevenueThisWeek(totalRevenueThisWeek)
                .orderCountsByStatus(orderCountsByStatus)
                .averageOrderValue(averageOrderValue)
                .bestSellingProductName(bestSellingProductName)
                .bestSellingProductQuantity(bestSellingProductQuantity)
                .averageRating(reviewSummary.getAverageRating() != null ? reviewSummary.getAverageRating() : 0.0)
                .totalRatings(reviewSummary.getReviewCount() != null ? reviewSummary.getReviewCount().intValue() : 0)
                .build();
    }
}