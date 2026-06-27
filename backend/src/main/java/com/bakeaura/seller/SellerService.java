package com.bakeaura.seller;

import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.map.MapService;
import com.bakeaura.product.ProductService;
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
public class SellerService {

    private final UserRepository userRepository;
    private final ProductService productService;
    private final SellerProfileRepository sellerProfileRepository;
    private final MapService mapService;

    public List<SellerProfileDto> getSellers() {
        return userRepository.findByRoleAndIsActiveTrue(Role.SELLER).stream()
                .map(this::toDto)
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
        return userRepository.findByRoleAndIsActiveTrue(Role.SELLER).stream()
                .filter(seller -> seller.getLatitude() != null && seller.getLongitude() != null)
                .filter(seller -> mapService.calculateDistance(
                        latitude, longitude,
                        seller.getLatitude(), seller.getLongitude()) <= radius)
                .map(this::toDto)
                .toList();
    }

    @Transactional
    @CacheEvict(value = "sellerProfiles", key = "#userId")
    public SellerProfileDto updateProfile(Long userId, UpdateSellerProfileDto request) {
        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (seller.getRole() != Role.SELLER) {
            throw new BadRequestException("User is not a seller");
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
    @CacheEvict(value = "sellerProfiles", key = "#userId")
    public SellerProfileDto toggleOpen(Long userId) {
        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (seller.getRole() != Role.SELLER) {
            throw new BadRequestException("User is not a seller");
        }

        SellerProfile profile = sellerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller profile not found. Please complete your profile first."));

        profile.setIsOpen(!profile.getIsOpen());
        sellerProfileRepository.save(profile);
        return toDto(seller);
    }

    @Transactional
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

    private SellerProfileDto toDto(User seller) {
        SellerProfile profile = sellerProfileRepository.findByUserId(seller.getId()).orElse(null);

        long productCount = productService.getProductsBySeller(seller.getId()).size();

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
                .email(seller.getEmail())
                .latitude(seller.getLatitude())
                .longitude(seller.getLongitude())
                .shopName(profile != null ? profile.getShopName() : null)
                .shopBio(profile != null ? profile.getShopBio() : null)
                .deliveryRadiusKm(profile != null ? profile.getDeliveryRadiusKm() : null)
                .isOpen(profile != null ? profile.getIsOpen() : false)
                .bannerImageUrl(profile != null ? profile.getBannerImageUrl() : null)
                .totalRatings(profile != null ? profile.getTotalRatings() : 0)
                .averageRating(profile != null ? profile.getAverageRating() : 0.0)
                .productCount(productCount)
                .profileCompleteness(completeness)
                .build();
    }
}