package com.bakeaura.auth;

import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor    //Any field marked final gets included in the constructor automatically.
public class AuthService {

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

        User savedUser = userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(
                savedUser.getId(),
                savedUser.getRole()
        );

        String refreshToken = jwtUtil.generateRefreshToken(
                savedUser.getId()
        );
        refreshTokenStore.store(savedUser.getId(), refreshToken);

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                savedUser.getEmail(),
                savedUser.getRole()
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
                accessToken,
                refreshToken,
                "Bearer",
                user.getEmail(),
                user.getRole()
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
                accessToken,
                newRefreshToken,
                "Bearer",
                user.getEmail(),
                user.getRole()
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
}
