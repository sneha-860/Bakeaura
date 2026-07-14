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

    // Per-IP bucket maps. MAX_ENTRIES prevents unbounded growth under heavy unique-IP traffic.
    // When the cap is hit the map is cleared — all IPs start fresh. Blunt but safe for a
    // single-instance deployment; swap for Redis-backed Bucket4j before multi-instance scale-out.
    private static final int MAX_ENTRIES_PER_MAP = 50_000;

    private final Map<String, Bucket> loginBuckets    = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> paymentBuckets  = new ConcurrentHashMap<>();
    private final Map<String, Bucket> resendBuckets   = new ConcurrentHashMap<>();
    private final Map<String, Bucket> aiBuckets       = new ConcurrentHashMap<>();

    private Bucket resolveBucket(Map<String, Bucket> buckets,
                                 String ip, long capacity, long minutes) {
        if (buckets.size() > MAX_ENTRIES_PER_MAP) {
            buckets.clear();
        }
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

        String ip     = extractIp(request);
        String path   = request.getRequestURI();
        String method = request.getMethod();

        Bucket bucket = null;

        if (method.equals("POST") && path.equals("/api/auth/login")) {
            bucket = resolveBucket(loginBuckets, ip, 5, 1);

        } else if (method.equals("POST") && path.equals("/api/auth/register")) {
            bucket = resolveBucket(registerBuckets, ip, 5, 1);

        } else if (method.equals("POST") && path.startsWith("/api/payments")) {
            bucket = resolveBucket(paymentBuckets, ip, 5, 1);

        } else if (method.equals("POST") && path.equals("/api/auth/resend-verification")) {
            bucket = resolveBucket(resendBuckets, ip, 3, 15);

        } else if (method.equals("POST") && path.startsWith("/api/ai")) {
            // AI Cake Design: 3 requests per 10 minutes per IP — Gemini calls are costly
            bucket = resolveBucket(aiBuckets, ip, 3, 10);
        }

        if (bucket != null) {
            if (bucket.tryConsume(1)) {
                filterChain.doFilter(request, response);
            } else {
                log.warn("Rate limit exceeded for IP {} on path {}", ip, path);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\": \"Too many requests. Please try again later.\"}"
                );
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
