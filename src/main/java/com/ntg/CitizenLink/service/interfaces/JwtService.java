package com.ntg.CitizenLink.service.interfaces;

import org.springframework.security.core.userdetails.UserDetails;
import java.util.Map;


public interface JwtService {

    /**
     * Generates a signed JWT for an authenticated user.
     */
    String generateToken(UserDetails userDetails, Map<String, Object> extraClaims);

    /**
     * Generates a signed JWT with no extra claims.
     */
    String generateToken(UserDetails userDetails);

    /**
     * Validates a token against a UserDetails object.
     */
    boolean isTokenValid(String token, UserDetails userDetails);

    /**
     * Extracts the username from a token.
     */
    String extractUsername(String token);

    /**
     * Extracts the role from a token.
     */
    String extractRole(String token);
}
