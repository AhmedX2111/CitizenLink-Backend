package com.ntg.citizenlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/**
 * Request body for POST /api/v1/auth/login (BRD AUTH-01).
 * Validation annotations let Spring return 400 with field errors
 * before the service layer is even reached.
 *
 * L-07: username/password are capped to the same upper bounds as
 * CreateUserRequest so an unauthenticated caller cannot push an arbitrarily
 * large password into BCrypt on every attempt.
 */
public record LoginRequest(

        @NotBlank(message = "Username is required")
        @Size(max = 100, message = "Username must not exceed 100 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password must not exceed 128 characters")
        String password
) {}
