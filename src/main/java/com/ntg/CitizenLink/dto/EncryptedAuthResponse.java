package com.ntg.CitizenLink.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ntg.CitizenLink.enums.UserRole;

/**
 * Response body with encrypted ID for POST /api/v1/auth/login and GET /api/v1/auth/me.
 * ID is encrypted to prevent exposure of internal UUIDs.
 */
public record EncryptedAuthResponse(
        String    token,           // JWT; null on /auth/me
        @JsonProperty("id")
        String    encryptedId,     // Encrypted ID instead of plain UUID
        String    username,
        String    displayName,
        String    email,
        UserRole role
) {

    /**
     * Factory method to create from plain AuthResponse
     */
    public static EncryptedAuthResponse fromAuthResponse(
            AuthResponse response,
            String encryptedId
    ) {
        return new EncryptedAuthResponse(
                response.token(),
                encryptedId,
                response.username(),
                response.displayName(),
                response.email(),
                response.role()
        );
    }
}
