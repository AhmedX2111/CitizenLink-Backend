package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.dto.AuthResponse;
import com.ntg.CitizenLink.dto.EncryptedAuthResponse;
import com.ntg.CitizenLink.dto.LoginRequest;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.enums.ActionStatus;
import com.ntg.CitizenLink.enums.EventType;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Handles authentication business logic.
 * Keeping this out of the controller makes the controller thin and testable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final IdEncryptionService idEncryptionService;
    private final AuditLogService auditLogService;

    /**
     * Authenticates credentials and returns a signed JWT + user profile with encrypted ID.
     */
    public EncryptedAuthResponse login(LoginRequest request) {
        log.debug("Login attempt for user: {}", request.username());

        try {
            // 1. Delegate credential check to Spring Security
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(),
                            request.password()
                    )
            );

            // 2. Credentials OK — load full entity for the response body
            AppUser user = appUserRepository.findByUsername(request.username())
                    .orElseThrow();

            // Log successful login
            auditLogService.logAuthenticationEvent(
                    EventType.LOGIN_SUCCESS,
                    user.getUsername(),
                    user.getId(),
                    user.getRole().name(),
                    ActionStatus.SUCCESS,
                    null
            );

            // 3. Embed role claim
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
            String token = jwtService.generateToken(
                    userDetails,
                    Map.of("role", user.getRole().name())
            );

            // 4. Create response with encrypted ID
            AuthResponse authResponse = toAuthResponse(token, user);
            String encryptedId = idEncryptionService.encryptId(user.getId());

            log.info("Login successful for user: {}", user.getUsername());

            return EncryptedAuthResponse.fromAuthResponse(authResponse, encryptedId);

        } catch (BadCredentialsException e) {
            // Log failed login - wrong password
            auditLogService.logAuthenticationEvent(
                    EventType.LOGIN_FAILURE,
                    request.username(),
                    null,
                    null,
                    ActionStatus.FAILED,
                    "Bad credentials - Invalid password"
            );
            log.warn("Login failed for user: {} - Bad credentials", request.username());
            throw e;

        } catch (DisabledException e) {
            // Log failed login - account disabled
            auditLogService.logAuthenticationEvent(
                    EventType.LOGIN_FAILURE,
                    request.username(),
                    null,
                    null,
                    ActionStatus.FAILED,
                    "Account is disabled/inactive"
            );
            log.warn("Login failed for user: {} - Account disabled", request.username());
            throw e;

        } catch (Exception e) {
            // Log failed login - other errors
            auditLogService.logAuthenticationEvent(
                    EventType.LOGIN_FAILURE,
                    request.username(),
                    null,
                    null,
                    ActionStatus.FAILED,
                    "Login error: " + e.getClass().getSimpleName()
            );
            log.error("Login error for user: {}", request.username(), e);
            throw e;
        }
    }

    /**
     * Logs out a user by logging the event.
     * The actual token invalidation is handled client-side.
     */
    public void logout(UserDetails userDetails) {
        if (userDetails != null) {
            auditLogService.logAuthenticationEvent(
                    EventType.LOGOUT,
                    userDetails.getUsername(),
                    null,
                    null,
                    ActionStatus.SUCCESS,
                    null
            );
            log.info("User logged out: {}", userDetails.getUsername());
        }
    }

    /**
     * Returns the authenticated user's profile with encrypted ID.
     */
    public EncryptedAuthResponse getCurrentUser(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow();

        AuthResponse authResponse = toAuthResponse(null, user);
        String encryptedId = idEncryptionService.encryptId(user.getId());

        log.debug("Returning current user with encrypted ID: {}", user.getUsername());

        return EncryptedAuthResponse.fromAuthResponse(authResponse, encryptedId);
    }

    private AuthResponse toAuthResponse(String token, AppUser user) {
        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
