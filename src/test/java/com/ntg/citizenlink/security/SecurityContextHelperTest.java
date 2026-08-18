package com.ntg.citizenlink.security;

import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.security.config.SecurityContextHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SecurityContextHelper}.
 *
 * Regression for L-01: an anonymous token (AnonymousAuthenticationToken) must
 * never be treated as an authenticated user.
 */
@ExtendWith(MockitoExtension.class)
class SecurityContextHelperTest {

    @Mock
    private AppUserRepository appUserRepository;

    private SecurityContextHelper helper;

    private final AppUser user = new AppUser();

    @BeforeEach
    void setUp() {
        user.setUsername("agent-1");
        user.setPasswordHash("hash");
        user.setDisplayName("Agent One");
        user.setEmail("agent1@citizenlink.eg");
        user.setRole(UserRole.AGENT);
        user.setActive(true);
        helper = new SecurityContextHelper(appUserRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("agent-1", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_AGENT"))));
    }

    private void setAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("anon-key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    }

    @Nested
    class AuthenticatedUser {

        @Test
        void getAuthenticatedUser_returnsUser_forRealAuthentication() {
            when(appUserRepository.findByUsername("agent-1")).thenReturn(Optional.of(user));
            setAuthenticated();

            assertThat(helper.getAuthenticatedUser()).isEqualTo(user);
        }

        @Test
        void getAuthenticatedUsername_returnsUsername_forRealAuthentication() {
            setAuthenticated();

            assertThat(helper.getAuthenticatedUsername()).isEqualTo("agent-1");
        }

        @Test
        void hasRole_returnsTrue_whenRolePresent() {
            setAuthenticated();

            assertThat(helper.hasRole("AGENT")).isTrue();
        }
    }

    @Nested
    class AnonymousUser {

        @Test
        void getAuthenticatedUser_throws_forAnonymousToken() {
            setAnonymous();

            assertThatThrownBy(() -> helper.getAuthenticatedUser())
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void getAuthenticatedUsername_returnsNull_forAnonymousToken() {
            setAnonymous();

            assertThat(helper.getAuthenticatedUsername()).isNull();
        }

        @Test
        void hasRole_returnsFalse_forAnonymousToken() {
            setAnonymous();

            assertThat(helper.hasRole("AGENT")).isFalse();
        }
    }

    @Nested
    class NoAuthentication {

        @Test
        void getAuthenticatedUser_throws_whenContextEmpty() {
            assertThatThrownBy(() -> helper.getAuthenticatedUser())
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void getAuthenticatedUsername_returnsNull_whenContextEmpty() {
            assertThat(helper.getAuthenticatedUsername()).isNull();
        }

        @Test
        void hasRole_returnsFalse_whenContextEmpty() {
            assertThat(helper.hasRole("AGENT")).isFalse();
        }
    }
}