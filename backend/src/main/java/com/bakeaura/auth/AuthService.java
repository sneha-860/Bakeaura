package com.bakeaura.auth;

import com.bakeaura.notification.EmailService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenStore refreshTokenStore;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setIsActive(true);
        user.setIsEmailVerified(false);

        String verificationToken = UUID.randomUUID().toString();
        user.setEmailVerificationToken(verificationToken);
        user.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(24));

        User savedUser = userRepository.save(user);

        emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken);

        String accessToken = jwtUtil.generateAccessToken(
                savedUser.getId(),
                savedUser.getRole()
        );

        String refreshToken = jwtUtil.generateRefreshToken(
                savedUser.getId()
        );
        refreshTokenStore.store(savedUser.getId(), refreshToken);

        return new AuthResponse(
                savedUser.getId(),
                accessToken,
                refreshToken,
                "Bearer",
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getRole(),
                Boolean.TRUE.equals(savedUser.getIsEmailVerified())
        );
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadRequestException("Invalid email or password");
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("User account is inactive");
        }

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(),
                user.getRole()
        );

        String refreshToken = jwtUtil.generateRefreshToken(
                user.getId()
        );
        refreshTokenStore.store(user.getId(), refreshToken);

        return new AuthResponse(
                user.getId(),
                accessToken,
                refreshToken,
                "Bearer",
                user.getEmail(),
                user.getName(),
                user.getRole(),
                Boolean.TRUE.equals(user.getIsEmailVerified())
        );
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtUtil.isTokenValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BadRequestException("Invalid refresh token");
        }

        Long userId = jwtUtil.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (!refreshTokenStore.matches(user.getId(), refreshToken)) {
            throw new BadRequestException("Invalid refresh token");
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("User account is inactive");
        }

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(),
                user.getRole()
        );

        String newRefreshToken = jwtUtil.generateRefreshToken(
                user.getId()
        );
        refreshTokenStore.store(user.getId(), newRefreshToken);

        return new AuthResponse(
                user.getId(),
                accessToken,
                newRefreshToken,
                "Bearer",
                user.getEmail(),
                user.getName(),
                user.getRole(),
                Boolean.TRUE.equals(user.getIsEmailVerified())
        );
    }

    public void logout(LogoutRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtUtil.isTokenValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BadRequestException("Invalid refresh token");
        }

        Long userId = jwtUtil.extractUserId(refreshToken);
        refreshTokenStore.revoke(userId);
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("This verification link is invalid or has already been used."));

        if (user.getEmailVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This verification link has expired. Please request a new one.");
        }

        // Idempotent: concurrent calls (email client prefetch, React Strict Mode dev double-call)
        // both succeed because the token is kept in DB until it naturally expires.
        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            return;
        }

        user.setIsEmailVerified(true);
        // Intentionally NOT clearing the token — keeping it lets concurrent duplicate requests
        // (double-click, email client prefetch) find the user and see they're already verified.
        // The token expires naturally at emailVerificationTokenExpiry (24h from issue).
        // It is overwritten if the user requests a new verification link via resend.
        userRepository.save(user);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        log.info("Resend verification requested for: {}", email);
        userRepository.findByEmail(email).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
                log.info("Resend skipped — account already verified: {}", email);
                return;
            }
            String token = UUID.randomUUID().toString();
            user.setEmailVerificationToken(token);
            user.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
            userRepository.save(user);
            log.info("New verification token saved for: {}", email);
            emailService.sendVerificationEmail(user.getEmail(), token);
            log.info("Verification email dispatched for: {}", email);
        });
        log.info("Resend handler complete for: {}", email);
    }
}