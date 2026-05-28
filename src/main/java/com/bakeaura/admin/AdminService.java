package com.bakeaura.admin;

import com.bakeaura.category.CategoryRepository;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.order.OrderRepository;
import com.bakeaura.payment.PaymentRepository;
import com.bakeaura.product.ProductRepository;
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
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CategoryRepository categoryRepository;

    public AdminDashboardDto dashboard() {
        return new AdminDashboardDto(
                userRepository.count(),
                productRepository.count(),
                orderRepository.count(),
                paymentRepository.count(),
                categoryRepository.count()
        );
    }

    public List<UserDto> getUsers(Role role) {
        List<User> users = role == null ? userRepository.findAll() : userRepository.findByRole(role);
        return users.stream().map(userService::toDto).toList();
    }

    @Transactional
    public UserDto updateUserStatus(Long id, AdminUserStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setIsActive(request.getActive());
        return userService.toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto updateUserRole(Long id, Role role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setRole(role);
        return userService.toDto(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found");
        }
        userRepository.deleteById(id);
    }
}
