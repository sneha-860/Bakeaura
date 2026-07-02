package com.bakeaura.user;

import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bakeaura.notification.EmailService;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserDto getMe(Long userId) {
        return toDto(getById(userId));
    }

    @Transactional
    public UserDto updateProfile(Long userId, UserProfileUpdateRequest request) {
        User user = getById(userId);
        user.setName(request.getName().trim());
        user.setLatitude(request.getLatitude());
        user.setLongitude(request.getLongitude());
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void requestEmailChange(Long userId, ChangeEmailRequest request) {
        User user = getById(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        String newEmail = request.getNewEmail().trim().toLowerCase();

        if (user.getEmail().equals(newEmail)) {
            throw new BadRequestException("New email must be different from current email");
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new BadRequestException("Email is already in use");
        }

        String token = UUID.randomUUID().toString();

        user.setPendingEmail(newEmail);
        user.setPendingEmailToken(token);
        user.setPendingEmailTokenExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        emailService.sendEmailChangeVerification(newEmail, token);
    }

    @Transactional
    public void confirmEmailChange(String token) {
        User user = userRepository.findByPendingEmailToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired token"));

        if (user.getPendingEmailTokenExpiry() == null ||
                user.getPendingEmailTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Token has expired");
        }

        user.setEmail(user.getPendingEmail());
        user.setPendingEmail(null);
        user.setPendingEmailToken(null);
        user.setPendingEmailTokenExpiry(null);
        userRepository.save(user);
    }

    User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getIsActive(),
                user.getLatitude(),
                user.getLongitude(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public boolean isActiveSeller(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() == com.bakeaura.enums.Role.SELLER
                        && Boolean.TRUE.equals(user.getIsActive()))
                .orElse(false);
    }
}
