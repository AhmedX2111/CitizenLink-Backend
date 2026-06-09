package com.ntg.CitizenLink.security.config;

import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
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
@Component
@RequiredArgsConstructor
public class SecurityContextHelper {

    private final AppUserRepository appUserRepository;

    /**
     * Returns the full AppUser entity for the currently authenticated principal.
     * Throws UsernameNotFoundException (-> 401) if the principal is not found.
     */
    public AppUser getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName(); // returns UserDetails.getUsername() = username string

        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Authenticated user not found: " + username));
    }

    /**
     * Convenience method — returns only the UUID of the authenticated user.
     * Use this when the service layer only needs the ID.
     */
    public UUID getAuthenticatedUserId() {
        return getAuthenticatedUser().getId();
    }
}
