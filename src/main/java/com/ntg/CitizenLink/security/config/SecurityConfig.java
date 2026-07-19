package com.ntg.CitizenLink.security.config;

import com.ntg.CitizenLink.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration for CitizenLink.
 *
 * Design decisions:
 *   - Stateless sessions (no HttpSession) — JWT carries all auth state.
 *   - CSRF disabled — safe for stateless REST APIs that don't use cookies for auth.
 *   - @EnableMethodSecurity enables @PreAuthorize at controller/service level
 *     for fine-grained per-method role checks (e.g. ADMIN-only user CRUD).
 *
 * Public endpoints (no token required):
 *   POST /api/v1/auth/login  — obtain token
 *   GET  /api/v1/auth/me     — intentionally protected (needs valid token)
 *   GET  /                   — landing page (if served by Spring)
 *   Swagger UI & OpenAPI docs for local dev
 *
 * Role-based rules (from BRD §4.3 permission matrix):
 *   /api/v1/users/**         → ADMIN only
 *   /api/v1/reports/**       → ADMIN, SUPERVISOR
 *   All other /api/v1/**     → any authenticated user
 *
 *   Fine-grained rules (e.g. assign = SUPERVISOR/ADMIN only) are enforced
 *   in the service layer via @PreAuthorize, not here — keeps SecurityConfig
 *   focused on authentication boundaries, not business rules.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // activates @PreAuthorize, @PostAuthorize, @Secured
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService      userDetailsService;

    // -------------------------------------------------------------------------
    // Filter chain
    // -------------------------------------------------------------------------

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — not needed for stateless JWT APIs
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless — Spring Security will never create an HttpSession
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // URL-level authorization rules
                .authorizeHttpRequests(auth -> auth

                        // ── Public (no token required) ────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()

                        // Swagger/OpenAPI — disable in production via profile if needed
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // ── Role-restricted ───────────────────────────────────────
                        // User admin — ADMIN only (BRD USR-02)
                        .requestMatchers("/api/v1/users/**").hasRole("ADMIN")

                        // Reports — ADMIN and SUPERVISOR (BRD §4.3)
                        .requestMatchers("/api/v1/reports/**").hasAnyRole("ADMIN", "SUPERVISOR")

                        // Dashboard — any authenticated role; fine-grained role checks
                        // (e.g. HANDLER-only my-open-cases) enforced via @PreAuthorize
                        .requestMatchers("/api/v1/dashboard/**").authenticated()

                        // ── Everything else requires a valid token ─────────────────
                        .anyRequest().authenticated()
                )

                // Register JWT filter before Spring's own username/password filter
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // -------------------------------------------------------------------------
    // Authentication infrastructure
    // -------------------------------------------------------------------------

    /**
     * DaoAuthenticationProvider wires together:
     *   - UserDetailsService  → loads user from DB by username
     *   - PasswordEncoder     → BCrypt comparison on login
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Spring Security 6.4+: UserDetailsService is a required constructor argument.
        // setUserDetailsService() was removed in this version.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager is used by AuthController to trigger the actual
     * username+password check during login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt with default strength (10 rounds). Shared across the application —
     * used both here and in any service that creates/updates passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
