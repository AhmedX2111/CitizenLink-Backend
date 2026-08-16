package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.security.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtServiceImpl} — real JJWT signing with a fixed
 * test secret, so token generation/parsing/validation is exercised end to end
 * without Spring.
 *
 * Covers: access-token generation + claims, refresh-token generation + claims,
 * validation (valid access, refresh used as access = invalid, expired = invalid,
 * wrong subject = invalid), and malformed-token behaviour.
 *
 * NOT covered: the HTTP filter wiring (JwtAuthenticationFilterTest) and
 * database-backed flows (AuthServiceImplTest).
 */
class JwtServiceImplTest {

    private static final String SECRET =
            "test-secret-key-for-citizenlink-testing-0123456789abcdef";
    private static final String USERNAME = "handler01";

    private JwtProperties properties;
    private JwtServiceImpl jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(SECRET, 3600_000L, 604800_000L, true);
        jwtService = new JwtServiceImpl(properties);
        userDetails = new User(USERNAME, "password",
                List.of(new SimpleGrantedAuthority("ROLE_HANDLER")));
    }

    @Nested
    class GenerateAccessToken {

        @Test
        void shouldEmbedSubjectAndRoleClaim() {
            String token = jwtService.generateToken(userDetails,
                    Map.of("role", "HANDLER"));

            assertThat(token).isNotBlank();
            assertThat(jwtService.extractUsername(token)).isEqualTo(USERNAME);
            assertThat(jwtService.extractRole(token)).isEqualTo("HANDLER");
            assertThat(jwtService.extractTokenType(token)).isNull();
        }

        @Test
        void shouldGenerateToken_withNoExtraClaims() {
            String token = jwtService.generateToken(userDetails);

            assertThat(jwtService.extractUsername(token)).isEqualTo(USERNAME);
            assertThat(jwtService.extractRole(token)).isNull();
        }
    }

    @Nested
    class GenerateRefreshToken {

        @Test
        void shouldEmbedTypeAndJtiClaims() {
            String token = jwtService.generateRefreshToken(USERNAME, "jti-123");

            assertThat(jwtService.extractUsername(token)).isEqualTo(USERNAME);
            assertThat(jwtService.extractTokenType(token)).isEqualTo("refresh");
            assertThat(jwtService.extractJti(token)).isEqualTo("jti-123");
        }
    }

    @Nested
    class ValidateToken {

        @Test
        void shouldAcceptValidAccessToken() {
            String token = jwtService.generateToken(userDetails,
                    Map.of("role", "HANDLER"));

            assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
        }

        @Test
        void shouldRejectRefreshTokenUsedAsAccessToken() {
            String token = jwtService.generateRefreshToken(USERNAME, "jti-1");

            assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
        }

        @Test
        void shouldRejectExpiredToken() {
            JwtProperties expired = new JwtProperties(SECRET, -1000L, 604800_000L, true);
            JwtServiceImpl expiredService = new JwtServiceImpl(expired);

            String token = expiredService.generateToken(userDetails,
                    Map.of("role", "HANDLER"));

            assertThat(expiredService.isTokenValid(token, userDetails)).isFalse();
        }

        @Test
        void shouldRejectTokenForDifferentUser() {
            String token = jwtService.generateToken(userDetails, Map.of("role", "HANDLER"));
            UserDetails other = new User("other", "password",
                    List.of(new SimpleGrantedAuthority("ROLE_AGENT")));

            assertThat(jwtService.isTokenValid(token, other)).isFalse();
        }
    }

    @Nested
    class MalformedTokens {

        @Test
        void shouldThrowOnGarbageToken_whenExtractingClaim() {
            assertThatThrownBy(() -> jwtService.extractUsername("not-a-jwt"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        void shouldReturnFalseOnGarbageToken_whenValidating() {
            assertThat(jwtService.isTokenValid("not-a-jwt", userDetails)).isFalse();
        }
    }
}
