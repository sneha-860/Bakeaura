package com.bakeaura.user;

import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserDto getMe(String email) {
        return toDto(getByEmail(email));
    }

    @Transactional
    public UserDto updateProfile(String email, UserProfileUpdateRequest request) {
        User user = getByEmail(email);
        user.setName(request.getName().trim());
        user.setLatitude(request.getLatitude());
        user.setLongitude(request.getLongitude());
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = getByEmail(email);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    User getByEmail(String email) {
        return userRepository.findByEmail(email)
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
}
