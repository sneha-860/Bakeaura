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

    public void store(String email, String refreshToken) {
        redisTemplate.opsForValue().set(
                key(email),
                refreshToken,
                Duration.ofMillis(refreshTokenExpirationMs)
        );
    }

    public boolean matches(String email, String refreshToken) {
        Object storedToken = redisTemplate.opsForValue().get(key(email));
        return refreshToken.equals(storedToken);
    }

    public void revoke(String email) {
        redisTemplate.delete(key(email));
    }

    private String key(String email) {
        return REFRESH_TOKEN_PREFIX + email;
    }
}
