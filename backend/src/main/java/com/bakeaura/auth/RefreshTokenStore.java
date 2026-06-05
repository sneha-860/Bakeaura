package com.bakeaura.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String REFRESH_TOKEN_PREFIX = "refresh-token:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpirationMs;

    public void store(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                key(userId),
                refreshToken,
                Duration.ofMillis(refreshTokenExpirationMs)
        );
    }

    public boolean matches(Long userId, String refreshToken) {
        Object storedToken = redisTemplate.opsForValue().get(key(userId));
        return refreshToken.equals(storedToken);
    }

    public void revoke(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return REFRESH_TOKEN_PREFIX + userId;
    }
}
