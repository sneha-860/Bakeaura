package com.bakeaura.roleapplication;

import com.bakeaura.enums.ApplicationStatus;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.influencer.InfluencerProfileService;
import com.bakeaura.referral.ReferralCodeService;
import com.bakeaura.seller.SellerService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleApplicationService {

    private final RoleApplicationRepository roleApplicationRepository;
    private final SellerService sellerService;
    private final InfluencerProfileService influencerProfileService;
    private final ReferralCodeService referralCodeService;
    private final UserRepository userRepository;


    @Transactional
    public RoleApplicationResponse apply(Long userId, RoleApplicationRequest request) {
        User user = getActiveUserById(userId);
        Role requestedRole = request.getRequestedRole();

        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new BadRequestException("Please verify your email address before applying for a role");
        }

        if (requestedRole != Role.SELLER && requestedRole != Role.INFLUENCER) {
            throw new BadRequestException("Only SELLER or INFLUENCER applications are allowed");
        }

        if (user.getRole() == requestedRole) {
            throw new BadRequestException("User already has requested role");
        }

        boolean hasPendingApplication = roleApplicationRepository
                .existsByUserAndRequestedRoleAndStatus(user, requestedRole, ApplicationStatus.PENDING);

        if (hasPendingApplication) {
            throw new BadRequestException("Application is already pending");
        }

        user.setPhone(request.getPhone().trim());
        userRepository.save(user);

        RoleApplication application = new RoleApplication();
        application.setUser(user);
        application.setRequestedRole(requestedRole);
        application.setStatus(ApplicationStatus.PENDING);
        application.setMessage(request.getMessage());

        return toResponse(roleApplicationRepository.save(application));
    }

    public List<RoleApplicationResponse> getMyApplications(Long userId) {
        User user = getActiveUserById(userId);

        return roleApplicationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<RoleApplicationResponse> getApplications(ApplicationStatus status) {
        List<RoleApplication> applications = status == null
                ? roleApplicationRepository.findAllByOrderByCreatedAtDesc()
                : roleApplicationRepository.findByStatusOrderByCreatedAtDesc(status);

        return applications.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RoleApplicationResponse approve(Long applicationId, Long adminId, RoleApplicationReviewRequest request) {
        RoleApplication application = getPendingApplication(applicationId);
        User user = application.getUser();

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("Cannot approve an inactive user");
        }

        String adminEmail = userRepository.findById(adminId)
                .map(User::getEmail)
                .orElse("admin#" + adminId);

        user.setRole(application.getRequestedRole());
        application.setStatus(ApplicationStatus.APPROVED);
        application.setReviewNote(request.getReviewNote());
        application.setReviewedBy(adminEmail);
        application.setReviewedAt(LocalDateTime.now());

        userRepository.save(user);

        if (application.getRequestedRole() == Role.SELLER) {
            sellerService.createProfileForNewSeller(user);
        } else if (application.getRequestedRole() == Role.INFLUENCER) {
            influencerProfileService.createProfileForNewInfluencer(user);
            referralCodeService.generateAndSaveReferralCode(user);
        }

        return toResponse(roleApplicationRepository.save(application));
    }

    @Transactional
    public RoleApplicationResponse reject(Long applicationId, Long adminId, RoleApplicationReviewRequest request) {
        RoleApplication application = getPendingApplication(applicationId);

        String adminEmail = userRepository.findById(adminId)
                .map(User::getEmail)
                .orElse("admin#" + adminId);

        application.setStatus(ApplicationStatus.REJECTED);
        application.setReviewNote(request.getReviewNote());
        application.setReviewedBy(adminEmail);
        application.setReviewedAt(LocalDateTime.now());

        return toResponse(roleApplicationRepository.save(application));
    }

    private User getActiveUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("User account is inactive");
        }
        return user;
    }

    private RoleApplication getPendingApplication(Long applicationId) {
        RoleApplication application = roleApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BadRequestException("Application is already reviewed");
        }

        return application;
    }

    private RoleApplicationResponse toResponse(RoleApplication application) {
        User user = application.getUser();

        return new RoleApplicationResponse(
                application.getId(),
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                application.getRequestedRole(),
                application.getStatus(),
                application.getMessage(),
                application.getReviewNote(),
                application.getReviewedBy(),
                application.getReviewedAt(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}
