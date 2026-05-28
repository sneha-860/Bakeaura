package com.bakeaura.auth;

import com.bakeaura.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component   // Spring manages this class
public class JwtUtil {

    @Value("${jwt.secret}")  // reads from application.properties
    private String secretKey;

    @Value("${jwt.access-expiration}")  // access token expiry
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-expiration}") // refresh token expiry
    private long refreshTokenExpirationMs;


    // Generate access token
    public String generateAccessToken(String email, Role role) {

        return Jwts.builder()

                // token owner
                .subject(email)

                // custom role claim
                .claim("role", role.name())

                // token purpose
                .claim("tokenType", "access")

                // token creation time
                .issuedAt(new Date())

                // token expiry time
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + accessTokenExpirationMs
                        )
                )

                // sign token
                .signWith(getSigningKey())

                // generate JWT string
                .compact();
    }


    // Generate refresh token
    public String generateRefreshToken(String email) {

        return Jwts.builder()

                // token owner
                .subject(email)

                // token purpose
                .claim("tokenType", "refresh")

                // token creation time
                .issuedAt(new Date())

                // refresh token expiry
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + refreshTokenExpirationMs
                        )
                )

                // sign token
                .signWith(getSigningKey())

                // generate JWT string
                .compact();
    }


    // Extract email from token
    public String extractEmail(String token) {

        return Jwts.parser()

                // verify token signature
                .verifyWith(getSigningKey())

                // build parser
                .build()

                // parse token
                .parseSignedClaims(token)

                // get payload
                .getPayload()

                // get subject/email
                .getSubject();
    }


    // Check token validity
    public boolean isTokenValid(String token) {

        try {

            Jwts.parser()

                    // verify signature
                    .verifyWith(getSigningKey())

                    // build parser
                    .build()

                    // parse token
                    .parseSignedClaims(token);

            return true;

        } catch (Exception e) {

            return false;  // expired or tampered
        }
    }


    // Extract role from token
    public Role extractRole(String token) {

        String role = Jwts.parser()

                // verify signature
                .verifyWith(getSigningKey())

                // build parser
                .build()

                // parse token
                .parseSignedClaims(token)

                // get payload
                .getPayload()

                // get role claim
                .get("role", String.class);

        return role == null
                ? null
                : Role.valueOf(role);
    }


    // Check whether token is a refresh token
    public boolean isRefreshToken(String token) {

        String tokenType = extractAllClaims(token)
                .get("tokenType", String.class);

        return "refresh".equals(tokenType);
    }


    // Check whether token is an access token
    public boolean isAccessToken(String token) {

        String tokenType = extractAllClaims(token)
                .get("tokenType", String.class);

        return "access".equals(tokenType);
    }


    // Extract all claims/payload
    public Claims extractAllClaims(String token) {

        return Jwts.parser()

                // verify signature
                .verifyWith(getSigningKey())

                // build parser
                .build()

                // parse token
                .parseSignedClaims(token)

                // return payload
                .getPayload();
    }


    // Check token expiry
    public boolean isTokenExpired(String token) {

        Date expiration = extractAllClaims(token)
                .getExpiration();

        return expiration.before(new Date());
    }


    // Create signing key
    private SecretKey getSigningKey() {

        // convert string -> byte[]
        byte[] keyBytes =
                secretKey.getBytes(StandardCharsets.UTF_8);

        // create secure HMAC key
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
