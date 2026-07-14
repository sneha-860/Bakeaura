package com.bakeaura.config;

import com.bakeaura.auth.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // allows @PreAuthorize on individual methods
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthFilter jwtAuthFilter,
                                           JwtAuthEntryPoint jwtAuthEntryPoint) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                // CSRF disabled for REST APIs (not needed, we use JWT)

                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // STATELESS = no sessions stored on server (JWT handles this)

                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                // Forces 401 (not the Spring Security default of 403) for any unauthenticated
                // request, so the frontend's axios interceptor can correctly detect an expired
                // token and silently refresh it

                .authorizeHttpRequests(auth -> auth
                        // Public routes — no token needed
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/", "/error").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/payments/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/payments/config").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sellers", "/api/sellers/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/influencers/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/content/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reels/**").permitAll()

                        // Defense-in-depth for admin surface — independent of @EnableMethodSecurity.
                        // @PreAuthorize on AdminController is the primary guard; this is the fallback.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Everything else needs a valid JWT token
                        .anyRequest().authenticated()
                )

                // Add our JWT filter before Spring's default auth filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
        // BCrypt hashes passwords: "password123" → "$2a$10$xyz..."
        // The hash is DIFFERENT every time, but can be verified
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
