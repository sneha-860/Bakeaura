package com.bakeaura.roleapplication;

import com.bakeaura.enums.ApplicationStatus;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.influencer.InfluencerProfileService;
import com.bakeaura.notification.NotificationService;
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
    private final NotificationService notificationService;


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

        // Block if user already has a non-customer role — upgrading roles is not self-service
        if (user.getRole() == Role.SELLER || user.getRole() == Role.INFLUENCER) {
            if (user.getRole() == requestedRole) {
                throw new BadRequestException("You already have the " + requestedRole.name().toLowerCase() + " role");
            }
            throw new BadRequestException("You already have the " + user.getRole().name().toLowerCase() +
                    " role. Contact support to request a role change.");
        }

        // Block if any application (for any role) is already pending
        if (roleApplicationRepository.existsByUserAndStatus(user, ApplicationStatus.PENDING)) {
            throw new BadRequestException("You already have a pending application. Please wait for it to be reviewed.");
        }

        // Role-specific validation — admins need structured proof to verify applicants
        if (requestedRole == Role.INFLUENCER) {
            if (request.getSocialUrl() == null || request.getSocialUrl().isBlank()) {
                throw new BadRequestException("An Instagram or YouTube URL is required for influencer applications so we can verify your audience");
            }
            if (request.getFollowerCount() == null || request.getFollowerCount() < 1000) {
                throw new BadRequestException("You need at least 1,000 followers to apply as an influencer on Bakeaura");
            }
        }

        user.setPhone(request.getPhone().trim());
        userRepository.save(user);

        RoleApplication application = new RoleApplication();
        application.setUser(user);
        application.setRequestedRole(requestedRole);
        application.setStatus(ApplicationStatus.PENDING);
        application.setMessage(request.getMessage());
        application.setSocialUrl(request.getSocialUrl());
        application.setFollowerCount(request.getFollowerCount());

        RoleApplicationResponse response = toResponse(roleApplicationRepository.save(application));

        notificationService.notifyUser(
                userId,
                "APPLICATION_SUBMITTED",
                "Your " + requestedRole.name().toLowerCase() + " application has been submitted and is under review. We'll notify you once it's reviewed.",
                null
        );

        userRepository.findByRoleAndIsActiveTrue(Role.ADMIN).forEach(admin ->
                notificationService.notifyUser(
                        admin.getId(),
                        "NEW_ROLE_APPLICATION",
                        user.getName() + " has applied to become a " + requestedRole.name().toLowerCase() + ".",
                        null
                )
        );

        return response;
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

        RoleApplicationResponse response = toResponse(roleApplicationRepository.save(application));

        notificationService.notifyUser(
                user.getId(),
                "ROLE_APPROVED",
                "Your " + application.getRequestedRole().name().toLowerCase() +
                        " application has been approved! Log out and log back in to activate your new role.",
                null
        );

        return response;
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

        RoleApplicationResponse response = toResponse(roleApplicationRepository.save(application));

        String rejectionMessage = "Your " + application.getRequestedRole().name().toLowerCase() + " application was not approved." +
                (request.getReviewNote() != null && !request.getReviewNote().isBlank()
                        ? " Admin note: " + request.getReviewNote()
                        : " You may apply again after addressing any issues.");
        notificationService.notifyUser(
                application.getUser().getId(),
                "ROLE_REJECTED",
                rejectionMessage,
                null
        );

        return response;
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
                user.getPhone(),
                user.getRole(),
                application.getRequestedRole(),
                application.getStatus(),
                application.getMessage(),
                application.getSocialUrl(),
                application.getFollowerCount(),
                application.getReviewNote(),
                application.getReviewedBy(),
                application.getReviewedAt(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}
