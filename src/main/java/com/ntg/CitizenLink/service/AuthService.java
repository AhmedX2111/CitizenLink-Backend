package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.dto.AuthResponse;
import com.ntg.CitizenLink.dto.EncryptedAuthResponse;
import com.ntg.CitizenLink.dto.LoginRequest;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
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

    private final AuthenticationManager  authenticationManager;
    private final AppUserRepository      appUserRepository;
    private final UserDetailsService     userDetailsService;
    private final JwtService             jwtService;
    private final IdEncryptionService    idEncryptionService;

    /**
     * Authenticates credentials and returns a signed JWT + user profile with encrypted ID.
     */
    public EncryptedAuthResponse login(LoginRequest request) {
        log.debug("Login attempt for user: {}", request.username());

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

        // 3. Embed role claim so the filter never needs a DB call to check roles
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtService.generateToken(
                userDetails,
                Map.of("role", user.getRole().name())
        );

        // 4. Create response with encrypted ID
        AuthResponse authResponse = toAuthResponse(token, user);
        String encryptedId = idEncryptionService.encryptId(user.getId());

        log.debug("Login successful for user: {}, ID encrypted", user.getUsername());

        return EncryptedAuthResponse.fromAuthResponse(authResponse, encryptedId);
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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

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
