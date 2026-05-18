package com.bakeaura.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component   // @Component = Spring manages this class
public class JwtUtil {

    @Value("${jwt.secret}")  // reads from application.properties
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expirationMs;

    // Generate a token for a user
    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)          // who this token is for
                .issuedAt(new Date())    // when it was created
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())  // sign with our secret
                .compact();
    }

    // Extract email from token
    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    // Is the token still valid?
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey())
                    .build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;  // expired or tampered
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}