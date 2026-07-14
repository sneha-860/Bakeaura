package com.bakeaura.admin;

import com.bakeaura.auth.RefreshTokenStore;
import com.bakeaura.category.CategoryService;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.influencer.InfluencerProfileService;
import com.bakeaura.order.OrderService;
import com.bakeaura.payment.PaymentService;
import com.bakeaura.product.ProductService;
import com.bakeaura.referral.ReferralCodeService;
import com.bakeaura.seller.SellerService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserDto;
import com.bakeaura.user.UserRepository;
import com.bakeaura.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final CategoryService categoryService;
    private final SellerService sellerService;
    private final InfluencerProfileService influencerProfileService;
    private final ReferralCodeService referralCodeService;
    private final RefreshTokenStore refreshTokenStore;

    public AdminDashboardDto dashboard() {
        return new AdminDashboardDto(
                userRepository.count(),
                productService.countProducts(),
                orderService.countOrders(),
                paymentService.countPayments(),
                categoryService.countCategories()
        );
    }

    public Page<UserDto> getUsers(Role role, Pageable pageable) {
        Page<User> users = role == null
                ? userRepository.findAll(pageable)
                : userRepository.findByRole(role, pageable);
        return users.map(userService::toDto);
    }

    @Transactional
    public UserDto updateUserStatus(Long adminId, Long targetId, AdminUserStatusRequest request) {
        if (adminId.equals(targetId)) {
            throw new BadRequestException("Admins cannot change their own active status");
        }
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setIsActive(request.getActive());
        userRepository.save(user);
        // Revoke refresh token so the deactivated user cannot silently extend their session.
        // Their current access token (≤15 min TTL) will still work until it expires — acceptable.
        if (!Boolean.TRUE.equals(request.getActive())) {
            refreshTokenStore.revoke(targetId);
        }
        return userService.toDto(user);
    }

    @Transactional
    public UserDto updateUserRole(Long adminId, Long targetId, Role role) {
        if (adminId.equals(targetId)) {
            throw new BadRequestException("Admins cannot change their own role");
        }
        // Prevent accidental or malicious ADMIN promotion through this endpoint.
        // ADMIN accounts must be created directly in the database by a super-admin.
        if (role == Role.ADMIN) {
            throw new BadRequestException("Cannot promote users to ADMIN through this endpoint");
        }
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Role previousRole = user.getRole();
        user.setRole(role);
        userRepository.save(user);

        if (role == Role.SELLER && previousRole != Role.SELLER) {
            sellerService.createProfileForNewSeller(user);
        } else if (role == Role.INFLUENCER && previousRole != Role.INFLUENCER) {
            influencerProfileService.createProfileForNewInfluencer(user);
            referralCodeService.generateAndSaveReferralCode(user);
        } else if (previousRole == Role.SELLER && role != Role.SELLER) {
            // Close the shop when demoting from SELLER so it disappears from public listings immediately.
            sellerService.closeShopOnDemotion(targetId);
        }

        return userService.toDto(user);
    }

    public void evictCategoriesCache() {
        categoryService.evictCategoriesCache();
    }

    // Deactivates the user rather than hard-deleting, preserving order history and referential
    // integrity. The user is blocked from logging in immediately (login validates isActive).
    @Transactional
    public void deleteUser(Long adminId, Long targetId) {
        if (adminId.equals(targetId)) {
            throw new BadRequestException("Admins cannot deactivate their own account");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (target.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admin accounts cannot be deactivated through this action");
        }
        target.setIsActive(false);
        userRepository.save(target);
        refreshTokenStore.revoke(targetId);
    }
}
