package com.bakeaura.influencer;

import com.bakeaura.enums.Role;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.referral.ReferralCodeService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import com.bakeaura.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InfluencerProfileService {

    private final InfluencerProfileRepository influencerProfileRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final ReferralCodeService referralCodeService;

    public List<InfluencerPublicDto> getInfluencers() {
        return userRepository.findByRoleAndIsActiveTrue(Role.INFLUENCER).stream()
                .map(this::toPublicDto)
                .toList();
    }

    public InfluencerPublicDto getInfluencer(Long id) {
        User influencer = userRepository.findById(id)
                .filter(user -> user.getRole() == Role.INFLUENCER)
                .orElseThrow(() -> new ResourceNotFoundException("Influencer not found"));
        return toPublicDto(influencer);
    }

    private InfluencerPublicDto toPublicDto(User user) {
        InfluencerProfile profile = influencerProfileRepository.findByUserId(user.getId()).orElse(null);
        String activeCode = referralCodeService.getActiveCodeByInfluencerId(user.getId());
        return InfluencerPublicDto.builder()
                .id(user.getId())
                .name(user.getName())
                .profileImageUrl(user.getProfileImageUrl())
                .bio(user.getBio())
                .niche(profile != null ? profile.getNiche() : null)
                .instagramUrl(profile != null ? profile.getInstagramUrl() : null)
                .youtubeUrl(profile != null ? profile.getYoutubeUrl() : null)
                .followerCount(profile != null ? profile.getFollowerCount() : null)
                .totalReferrals(profile != null ? profile.getTotalReferrals() : null)
                .referralCode(activeCode)
                .build();
    }

    public void createProfileForNewInfluencer(User user) {
        if (influencerProfileRepository.existsByUserId(user.getId())) {
            return;
        }

        InfluencerProfile profile = new InfluencerProfile();
        profile.setUser(user);
        profile.setNiche("general");
        profile.setTotalReferrals(0L);
        profile.setTotalEarnings(BigDecimal.ZERO);
        influencerProfileRepository.save(profile);
    }

    public InfluencerProfileResponse getMyProfile(Long userId) {
        InfluencerProfile profile = influencerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Influencer profile not found"));
        return toResponse(profile);
    }

    @Transactional
    public InfluencerProfileResponse updateMyProfile(Long userId, InfluencerProfileUpdateRequest request) {
        InfluencerProfile profile = influencerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Influencer profile not found"));

        if (request.getNiche() != null) profile.setNiche(request.getNiche());
        if (request.getInstagramUrl() != null) profile.setInstagramUrl(request.getInstagramUrl());
        if (request.getYoutubeUrl() != null) profile.setYoutubeUrl(request.getYoutubeUrl());
        if (request.getFollowerCount() != null) profile.setFollowerCount(request.getFollowerCount());

        return toResponse(influencerProfileRepository.save(profile));
    }

    @Transactional
    public void incrementTotalReferrals(Long userId) {
        influencerProfileRepository.incrementTotalReferralsByUserId(userId);
    }

    private InfluencerProfileResponse toResponse(InfluencerProfile profile) {
        User user = profile.getUser();
        // Live balance from wallet_transactions — the stale totalEarnings column is never updated.
        BigDecimal walletBalance = walletService.getBalance(user.getId());
        return new InfluencerProfileResponse(
                profile.getId(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                profile.getNiche(),
                profile.getInstagramUrl(),
                profile.getYoutubeUrl(),
                profile.getFollowerCount(),
                profile.getTotalReferrals(),
                walletBalance,
                profile.getCreatedAt()
        );
    }
}
