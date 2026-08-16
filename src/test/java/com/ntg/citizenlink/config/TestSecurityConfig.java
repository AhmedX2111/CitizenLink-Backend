package com.ntg.citizenlink.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Security filter chain for {@code @WebMvcTest} slices.
 * Mimics the production SecurityConfig but is self-contained:
 *   - CSRF disabled (controller tests assert status codes, not CSRF semantics)
 *   - Public auth endpoints (login/refresh) permitted
 *   - Everything else requires authentication
 *   - Unauthenticated requests → 401 (matches production M-07 behaviour; the
 *     client's silent-refresh keys on 401, not the default 403)
 *   - {@code @EnableMethodSecurity} is required so {@code @PreAuthorize} on controllers works
 * Declared as {@code @TestConfiguration} so it is NOT picked up by component
 * scanning — {@code @SpringBootTest} integration tests must use the real
 * SecurityConfig. It is activated explicitly via {@code @Import} in {@code @WebMvcTest}.
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
