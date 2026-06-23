package com.ntg.CitizenLink.security.config;

import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the authenticated AppUser from the Spring Security context.
 *
 * Keeps UUID/AppUser resolution out of controllers and services.
 * Controllers call getAuthenticatedUser() or getAuthenticatedUserId()
 * instead of touching SecurityContextHolder or UserDetails directly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityContextHelper {

    private final AppUserRepository appUserRepository;

    /**
     * Returns the full AppUser entity for the currently authenticated principal.
     * Throws UsernameNotFoundException (-> 401) if the principal is not found.
     */
    public AppUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("No authenticated user found in SecurityContext");
            throw new UsernameNotFoundException("No authenticated user found");
        }

        String username = authentication.getName();

        log.debug("Resolving authenticated user: {}", username);

        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("Authenticated user not found in database: {}", username);
                    return new UsernameNotFoundException("Authenticated user not found: " + username);
                });
    }

    /**
     * Convenience method — returns only the UUID of the authenticated user.
     * Use this when the service layer only needs the ID.
     */
    public UUID getAuthenticatedUserId() {
        return getAuthenticatedUser().getId();
    }

    /**
     * Returns the username of the authenticated user.
     */
    public String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }

    /**
     * Checks if the current user has a specific role.
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}