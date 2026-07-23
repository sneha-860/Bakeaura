package com.bakeaura.referral;

import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferralCodeService {

    private final ReferralCodeRepository referralCodeRepository;

    public void generateAndSaveReferralCode(User influencer) {
        String code = generateUniqueCode(influencer);
        ReferralCode referralCode = new ReferralCode();
        referralCode.setInfluencer(influencer);
        referralCode.setCode(code);
        referralCode.setIsActive(true);
        referralCodeRepository.save(referralCode);
    }

    public ReferralCode getActiveCodeByCode(String code) {
        ReferralCode referralCode = referralCodeRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Referral code not found"));
        if (!Boolean.TRUE.equals(referralCode.getIsActive())) {
            throw new BadRequestException("Referral code is inactive");
        }
        return referralCode;
    }

    public List<ReferralCodeResponse> getMyActiveCodes(Long influencerId) {
        return referralCodeRepository.findByInfluencerIdAndIsActiveTrue(influencerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public String getActiveCodeByInfluencerId(Long influencerId) {
        return referralCodeRepository.findByInfluencerIdAndIsActiveTrue(influencerId)
                .stream().findFirst().map(ReferralCode::getCode).orElse(null);
    }

    private String generateUniqueCode(User influencer) {
        String namePart = influencer.getName()
                .toUpperCase()
                .replaceAll("[^A-Z]", "");
        namePart = namePart.substring(0, Math.min(6, namePart.length()));

        int year = LocalDateTime.now().getYear();
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < 10; i++) {
            String suffix = String.valueOf(alphabet.charAt(random.nextInt(26)))
                    + alphabet.charAt(random.nextInt(26));
            String code = namePart + year + suffix;
            if (!referralCodeRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new BadRequestException("Could not generate a unique referral code after 10 attempts — please retry the role promotion");
    }

    private ReferralCodeResponse toResponse(ReferralCode referralCode) {
        return new ReferralCodeResponse(
                referralCode.getId(),
                referralCode.getInfluencer().getId(),
                referralCode.getInfluencer().getName(),
                referralCode.getCode(),
                referralCode.getIsActive(),
                referralCode.getCreatedAt()
        );
    }
}
