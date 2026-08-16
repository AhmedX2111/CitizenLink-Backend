package com.ntg.citizenlink.dto;

import com.ntg.citizenlink.enums.UserRole;
import java.util.UUID;

/**
 * Response body for POST /api/v1/auth/login and GET /api/v1/auth/me (BRD AUTH-02).
 *
 * The token field is null on /auth/me — the client already has it.
 * Sending it again on /me would be redundant and slightly wasteful.
 *
 * Never include passwordHash in any response DTO.
 */
public record AuthResponse(
        String    token,          // access JWT; null on /auth/me
        String    refreshToken,   // refresh JWT; null on /auth/me
        UUID      id,
        String    username,
        String    displayName,
        String    email,
        UserRole  role
) {}
