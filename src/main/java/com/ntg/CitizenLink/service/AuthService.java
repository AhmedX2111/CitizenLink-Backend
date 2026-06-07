package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.dto.AuthResponse;
import com.ntg.CitizenLink.dto.LoginRequest;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager  authenticationManager;
    private final AppUserRepository      appUserRepository;
    private final UserDetailsService     userDetailsService;
    private final JwtService             jwtService;

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    /**
     * Authenticates credentials and returns a signed JWT + user profile.
     *
     * authenticate() will throw:
     *   - BadCredentialsException   if username/password is wrong
     *   - DisabledException         if AppUser.active = false
     * Both propagate as 401 (handled by Spring Security's default error response).
     */
    public AuthResponse login(LoginRequest request) {
        // 1. Delegate credential check to Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        // 2. Credentials OK — load full entity for the response body
        AppUser user = appUserRepository.findByUsername(request.username())
                .orElseThrow(); // can't reach here if authenticate() succeeded

        // 3. Embed role claim so the filter never needs a DB call to check roles
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtService.generateToken(
                userDetails,
                Map.of("role", user.getRole().name())
        );

        return toAuthResponse(token, user);
    }

    // -------------------------------------------------------------------------
    // Current user (/auth/me)
    // -------------------------------------------------------------------------

    /**
     * Returns the authenticated user's profile without re-issuing a token.
     * The username comes from SecurityContextHolder (populated by the filter).
     */
    public AuthResponse getCurrentUser(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow();
        return toAuthResponse(null, user);  // token = null on /me
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
