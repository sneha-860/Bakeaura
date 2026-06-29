package com.bakeaura.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // Each IP address gets its own bucket for each endpoint category.
    // ConcurrentHashMap is thread-safe — multiple requests can arrive
    // simultaneously and the map handles them without data corruption.
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> paymentBuckets = new ConcurrentHashMap<>();

    // Creates a bucket that allows 'capacity' tokens,
    // refilled fully every 'minutes' minutes.
    // computeIfAbsent means: if this IP already has a bucket, return it.
    // If not, create a new one and store it.
    private Bucket resolveBucket(Map<String, Bucket> buckets,
                                 String ip, long capacity, long minutes) {
        return buckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(capacity)
                                .refillGreedy(capacity, Duration.ofMinutes(minutes))
                                .build())
                        .build()
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String ip = extractIp(request);
        String path = request.getRequestURI();
        String method = request.getMethod();

        Bucket bucket = null;

        // Match the request to the correct bucket category.
        // Only POST requests to sensitive endpoints are rate limited.
        if (method.equals("POST") && path.equals("/api/auth/login")) {
            bucket = resolveBucket(loginBuckets, ip, 5, 1);

        } else if (method.equals("POST") && path.equals("/api/auth/register")) {
            bucket = resolveBucket(registerBuckets, ip, 5, 1);

        } else if (method.equals("POST") && path.startsWith("/api/payments")) {
            bucket = resolveBucket(paymentBuckets, ip, 5, 1);

        }
        // If this request matches a rate-limited endpoint,
        // try to consume one token from the bucket.
        if (bucket != null) {
            if (bucket.tryConsume(1)) {
                // Token consumed successfully — let the request through.
                filterChain.doFilter(request, response);
            } else {
                // No tokens left — reject with 429.
                log.warn("Rate limit exceeded for IP {} on path {}", ip, path);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\": \"Too many requests. Please try again later.\"}"
                );
            }
        } else {
            // Not a rate-limited endpoint — pass through immediately.
            filterChain.doFilter(request, response);
        }
    }

    // Extract the real client IP address.
    // X-Forwarded-For is set by reverse proxies like Nginx or Railway.
    // If absent, fall back to the direct connection address.
    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For can contain multiple IPs if there are
            // multiple proxies: "clientIP, proxy1IP, proxy2IP"
            // We only want the first one — the actual client.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}