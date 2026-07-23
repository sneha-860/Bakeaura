package com.bakeaura.auth;

import com.bakeaura.enums.Role;
import com.bakeaura.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                // One parse — signature verified once. Claims hold both the tokenType and subject.
                Claims claims = jwtUtil.extractAllClaims(token);
                if ("access".equals(claims.get("tokenType", String.class))
                        && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Long userId = Long.parseLong(claims.getSubject());
                    userRepository.findById(userId).ifPresent(user -> {
                        if (!Boolean.TRUE.equals(user.getIsActive())) return;
                        // Role is always read from DB so that an admin role-change
                        // takes effect on the very next request without waiting for
                        // the access token to expire.
                        Role role = user.getRole();
                        List<SimpleGrantedAuthority> authorities = role == null
                                ? List.of()
                                : List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userId, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
                }
            } catch (Exception ignored) {
                // expired or tampered token — proceed without setting authentication
            }
        }

        filterChain.doFilter(request, response);
    }
}
