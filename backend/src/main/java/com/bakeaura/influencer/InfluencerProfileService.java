package com.bakeaura.influencer;

import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InfluencerProfileService {

    private final InfluencerProfileRepository influencerProfileRepository;

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

    private InfluencerProfileResponse toResponse(InfluencerProfile profile) {
        User user = profile.getUser();
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
                profile.getTotalEarnings(),
                profile.getCreatedAt()
        );
    }
}
