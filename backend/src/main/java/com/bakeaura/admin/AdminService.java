package com.bakeaura.admin;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public AdminDashboardDto dashboard() {
        return new AdminDashboardDto(
                userRepository.count(),
                productService.countProducts(),
                orderService.countOrders(),
                paymentService.countPayments(),
                categoryService.countCategories()
        );
    }

    public List<UserDto> getUsers(Role role) {
        List<User> users = role == null ? userRepository.findAll() : userRepository.findByRole(role);
        return users.stream().map(userService::toDto).toList();
    }

    @Transactional
    public UserDto updateUserStatus(Long adminId, Long targetId, AdminUserStatusRequest request) {
        if (adminId.equals(targetId)) {
            throw new BadRequestException("Admins cannot change their own active status");
        }
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setIsActive(request.getActive());
        return userService.toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto updateUserRole(Long adminId, Long targetId, Role role) {
        if (adminId.equals(targetId)) {
            throw new BadRequestException("Admins cannot change their own role");
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
        }

        return userService.toDto(user);
    }

    public void evictCategoriesCache() {
        categoryService.evictCategoriesCache();
    }

    @Transactional
    public void deleteUser(Long adminId, Long targetId) {
        if (adminId.equals(targetId)) {
            throw new BadRequestException("Admins cannot delete their own account");
        }
        if (!userRepository.existsById(targetId)) {
            throw new ResourceNotFoundException("User not found");
        }
        userRepository.deleteById(targetId);
    }
}
