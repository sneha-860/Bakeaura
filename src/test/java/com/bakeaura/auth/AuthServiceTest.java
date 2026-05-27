package com.bakeaura.auth;

import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil, refreshTokenStore);
    }

    @Test
    void registerCreatesActiveCustomerAndStoresRefreshToken() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Test User");
        request.setEmail("test@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateAccessToken("test@example.com", Role.CUSTOMER)).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("test@example.com")).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getName()).isEqualTo("Test User");
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(savedUser.getIsActive()).isTrue();

        verify(refreshTokenStore).store("test@example.com", "refresh-token");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email is already registered");

        verify(userRepository, never()).save(any());
        verify(refreshTokenStore, never()).store(any(), any());
    }

    @Test
    void loginRejectsWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrong-password");

        User user = activeUser(Role.CUSTOMER);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid email or password");

        verify(refreshTokenStore, never()).store(any(), any());
    }

    @Test
    void loginRejectsInactiveUser() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User user = activeUser(Role.CUSTOMER);
        user.setIsActive(false);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("User account is inactive");

        verify(refreshTokenStore, never()).store(any(), any());
    }

    @Test
    void loginReturnsTokensAndStoresRefreshToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User user = activeUser(Role.SELLER);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateAccessToken("test@example.com", Role.SELLER)).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("test@example.com")).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        verify(refreshTokenStore).store("test@example.com", "refresh-token");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getRole()).isEqualTo(Role.SELLER);
    }

    @Test
    void refreshRejectsAccessToken() {
        RefreshTokenRequest request = refreshRequest("access-token");

        when(jwtUtil.isTokenValid("access-token")).thenReturn(true);
        when(jwtUtil.isRefreshToken("access-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenStore, never()).matches(any(), any());
    }

    @Test
    void refreshRejectsTokenNotStoredInRedis() {
        RefreshTokenRequest request = refreshRequest("refresh-token");
        User user = activeUser(Role.CUSTOMER);

        when(jwtUtil.isTokenValid("refresh-token")).thenReturn(true);
        when(jwtUtil.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtUtil.extractEmail("refresh-token")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(refreshTokenStore.matches("test@example.com", "refresh-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void refreshRejectsInactiveUser() {
        RefreshTokenRequest request = refreshRequest("refresh-token");
        User user = activeUser(Role.CUSTOMER);
        user.setIsActive(false);

        when(jwtUtil.isTokenValid("refresh-token")).thenReturn(true);
        when(jwtUtil.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtUtil.extractEmail("refresh-token")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(refreshTokenStore.matches("test@example.com", "refresh-token")).thenReturn(true);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("User account is inactive");

        verify(refreshTokenStore, never()).store(any(), any());
    }

    @Test
    void refreshRotatesRefreshToken() {
        RefreshTokenRequest request = refreshRequest("old-refresh-token");
        User user = activeUser(Role.ADMIN);

        when(jwtUtil.isTokenValid("old-refresh-token")).thenReturn(true);
        when(jwtUtil.isRefreshToken("old-refresh-token")).thenReturn(true);
        when(jwtUtil.extractEmail("old-refresh-token")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(refreshTokenStore.matches("test@example.com", "old-refresh-token")).thenReturn(true);
        when(jwtUtil.generateAccessToken("test@example.com", Role.ADMIN)).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken("test@example.com")).thenReturn("new-refresh-token");

        AuthResponse response = authService.refresh(request);

        verify(refreshTokenStore).store("test@example.com", "new-refresh-token");
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void logoutRevokesStoredRefreshToken() {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refresh-token");

        when(jwtUtil.isTokenValid("refresh-token")).thenReturn(true);
        when(jwtUtil.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtUtil.extractEmail("refresh-token")).thenReturn("test@example.com");

        authService.logout(request);

        verify(refreshTokenStore).revoke("test@example.com");
    }

    private RefreshTokenRequest refreshRequest(String refreshToken) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);
        return request;
    }

    private User activeUser(Role role) {
        User user = new User();
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPassword("encoded-password");
        user.setRole(role);
        user.setIsActive(true);
        return user;
    }
}
