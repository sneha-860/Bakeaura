package com.bakeaura.roleapplication;

import com.bakeaura.enums.ApplicationStatus;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
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
    private final UserRepository userRepository;

    @Transactional
    public RoleApplicationResponse apply(String email, RoleApplicationRequest request) {
        User user = getActiveUserByEmail(email);
        Role requestedRole = request.getRequestedRole();

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

        RoleApplication application = new RoleApplication();
        application.setUser(user);
        application.setRequestedRole(requestedRole);
        application.setStatus(ApplicationStatus.PENDING);
        application.setMessage(request.getMessage());

        return toResponse(roleApplicationRepository.save(application));
    }

    public List<RoleApplicationResponse> getMyApplications(String email) {
        User user = getActiveUserByEmail(email);

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
    public RoleApplicationResponse approve(Long applicationId, String adminEmail, RoleApplicationReviewRequest request) {
        RoleApplication application = getPendingApplication(applicationId);
        User user = application.getUser();

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("Cannot approve an inactive user");
        }

        user.setRole(application.getRequestedRole());
        application.setStatus(ApplicationStatus.APPROVED);
        application.setReviewNote(request.getReviewNote());
        application.setReviewedBy(adminEmail);
        application.setReviewedAt(LocalDateTime.now());

        userRepository.save(user);

        if (application.getRequestedRole() == Role.SELLER) {
            sellerService.createProfileForNewSeller(user);
        }

        return toResponse(roleApplicationRepository.save(application));
    }

    @Transactional
    public RoleApplicationResponse reject(Long applicationId, String adminEmail, RoleApplicationReviewRequest request) {
        RoleApplication application = getPendingApplication(applicationId);

        application.setStatus(ApplicationStatus.REJECTED);
        application.setReviewNote(request.getReviewNote());
        application.setReviewedBy(adminEmail);
        application.setReviewedAt(LocalDateTime.now());

        return toResponse(roleApplicationRepository.save(application));
    }

    private User getActiveUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
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
