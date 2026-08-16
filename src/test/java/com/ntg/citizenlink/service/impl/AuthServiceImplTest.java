package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.dto.EncryptedAuthResponse;
import com.ntg.citizenlink.dto.LoginRequest;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.exception.ResourceNotFoundException;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.service.interfaces.IdEncryptionService;
import com.ntg.citizenlink.service.interfaces.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl} — mocks all collaborators
 * (AuthenticationManager, repositories, JwtService, IdEncryptionService).
 * Covers: login (success, bad credentials, disabled account, missing user),
 * refresh token rotation (success, invalid format, wrong type, missing user,
 * disabled user, revoked/reused JTI), logout, and getCurrentUser.
 * NOT covered here: the HTTP/cookie layer (see AuthControllerTest, @WebMvcTest),
 * and real JWT signing (see JwtServiceImplTest).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private AppUserRepository appUserRepository;
    @Mock private UserDetailsService userDetailsService;
    @Mock private JwtService jwtService;
    @Mock private IdEncryptionService idEncryptionService;

    @InjectMocks private AuthServiceImpl authService;

    private static final String USERNAME = "agent01";
    private static final String PASSWORD = "password123";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String ENCRYPTED_ID = "encrypted-id";
    private static final UUID USER_ID = UUID.randomUUID();

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setPasswordHash("$2a$10$hashed");
        user.setDisplayName("Agent One");
        user.setEmail("agent01@citizenlink.gov");
        user.setRole(UserRole.AGENT);
        user.setActive(true);
    }

    private UserDetails userDetails() {
        return new User(USERNAME, PASSWORD, List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // login()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class Login {

        @Test
        void shouldReturnAuthResponseWithAccessToken_whenCredentialsValid() {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(null);
            when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails());
            when(jwtService.generateToken(eq(userDetails()), any())).thenReturn(ACCESS_TOKEN);
            when(jwtService.generateRefreshToken(eq(USERNAME), any())).thenReturn(REFRESH_TOKEN);
            when(idEncryptionService.encryptId(USER_ID)).thenReturn(ENCRYPTED_ID);

            EncryptedAuthResponse response = authService.login(new LoginRequest(USERNAME, PASSWORD));

            assertThat(response.token()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.username()).isEqualTo(USERNAME);
            assertThat(response.role()).isEqualTo(UserRole.AGENT);
            assertThat(response.encryptedId()).isEqualTo(ENCRYPTED_ID);
            assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
        }

        @Test
        void shouldPersistRefreshTokenJti_onSuccessfulLogin() {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(null);
            when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails());
            when(jwtService.generateToken(eq(userDetails()), any())).thenReturn(ACCESS_TOKEN);
            when(jwtService.generateRefreshToken(eq(USERNAME), any())).thenReturn(REFRESH_TOKEN);
            when(idEncryptionService.encryptId(USER_ID)).thenReturn(ENCRYPTED_ID);

            authService.login(new LoginRequest(USERNAME, PASSWORD));

            ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
            verify(appUserRepository).save(captor.capture());
            assertThat(captor.getValue().getRefreshTokenJti()).isNotBlank();
        }

        @Test
        void shouldThrowBadCredentials_whenAuthenticationFails() {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));
            LoginRequest request = new LoginRequest(USERNAME, PASSWORD);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class);
            verify(appUserRepository, never()).save(any());
        }

        @Test
        void shouldThrowBadCredentials_whenUserMissingAfterAuthSucceeds() {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(null);
            when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            LoginRequest request = new LoginRequest(USERNAME, PASSWORD);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        void shouldPropagateDisabledException_whenAccountDisabled() {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new DisabledException("disabled"));
            LoginRequest request = new LoginRequest(USERNAME, PASSWORD);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(DisabledException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // refreshToken()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class RefreshToken {

        private static final String JTI = "some-jti";

        @BeforeEach
        void setUpJwtParsing() {
            // lenient: the malformed-token test overrides extractUsername with
            // thenThrow(), which makes these stubs unused for that scenario.
            lenient().when(jwtService.extractUsername(REFRESH_TOKEN)).thenReturn(USERNAME);
            lenient().when(jwtService.extractJti(REFRESH_TOKEN)).thenReturn(JTI);
            lenient().when(jwtService.extractTokenType(REFRESH_TOKEN)).thenReturn("refresh");
        }

        @Test
        void shouldRotateRefreshToken_onValidToken() {
            user.setRefreshTokenJti(JTI);
            when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails());
            when(jwtService.generateToken(eq(userDetails()), any())).thenReturn(ACCESS_TOKEN);
            when(jwtService.generateRefreshToken(eq(USERNAME), any())).thenReturn(REFRESH_TOKEN);
            when(idEncryptionService.encryptId(USER_ID)).thenReturn(ENCRYPTED_ID);

            EncryptedAuthResponse response = authService.refreshToken(REFRESH_TOKEN);

            assertThat(response.token()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.username()).isEqualTo(USERNAME);

            ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
            verify(appUserRepository).save(captor.capture());
            assertThat(captor.getValue().getRefreshTokenJti()).isNotBlank();
            assertThat(captor.getValue().getRefreshTokenJti()).isNotEqualTo(JTI);
        }

        @Test
        void shouldThrowBadCredentials_whenTokenMalformed() {
            when(jwtService.extractUsername(REFRESH_TOKEN)).thenThrow(new RuntimeException("bad token"));

            assertThatThrownBy(() -> authService.refreshToken(REFRESH_TOKEN))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid refresh token");
        }

        @Test
        void shouldThrowBadCredentials_whenTokenNotRefreshType() {
            when(jwtService.extractTokenType(REFRESH_TOKEN)).thenReturn("access");

            assertThatThrownBy(() -> authService.refreshToken(REFRESH_TOKEN))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid refresh token");
        }

        @Test
        void shouldThrowBadCredentials_whenUserNotFound() {
            when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken(REFRESH_TOKEN))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid refresh token");
        }

        @Test
        void shouldClearJtiAndThrow_whenUserDisabled() {
            user.setActive(false);
            user.setRefreshTokenJti(JTI);
            when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.refreshToken(REFRESH_TOKEN))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Account is disabled");

            verify(appUserRepository).save(user);
            assertThat(user.getRefreshTokenJti()).isNull();
        }

        @Test
        void shouldClearJtiAndThrow_whenTokenReusedOrRevoked() {
            user.setRefreshTokenJti("different-jti");
            when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.refreshToken(REFRESH_TOKEN))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Refresh token has been revoked");

            verify(appUserRepository).save(user);
            assertThat(user.getRefreshTokenJti()).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // logout()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class Logout {

        @Test
        void shouldClearJti_whenUserExists() {
            user.setRefreshTokenJti("jti");
            when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            authService.logout(userDetails());

            verify(appUserRepository).save(user);
            assertThat(user.getRefreshTokenJti()).isNull();
        }

        @Test
        void shouldDoNothing_whenUserDetailsNull() {
            authService.logout(null);

            verify(appUserRepository, never()).findByUsername(any());
            verify(appUserRepository, never()).save(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getCurrentUser()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class GetCurrentUser {

        @Test
        void shouldReturnProfile_whenUserExists() {
            when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(idEncryptionService.encryptId(USER_ID)).thenReturn(ENCRYPTED_ID);

            EncryptedAuthResponse response = authService.getCurrentUser(USERNAME);

            assertThat(response.username()).isEqualTo(USERNAME);
            assertThat(response.role()).isEqualTo(UserRole.AGENT);
            assertThat(response.encryptedId()).isEqualTo(ENCRYPTED_ID);
            assertThat(response.token()).isNull();
            assertThat(response.refreshToken()).isNull();
        }

        @Test
        void shouldThrowNotFound_whenUserMissing() {
            when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getCurrentUser(USERNAME))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
