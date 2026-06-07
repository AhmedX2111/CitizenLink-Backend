package com.ntg.CitizenLink.dto;

import jakarta.validation.constraints.NotBlank;


/**
 * Request body for POST /api/v1/auth/login (BRD AUTH-01).
 * Validation annotations let Spring return 400 with field errors
 * before the service layer is even reached.
 */
public record LoginRequest(

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {}
