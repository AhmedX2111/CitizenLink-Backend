package com.ntg.CitizenLink.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the jwt.* block from application.yml/properties.
 *
 * Security: All secrets must be provided via environment variables.
 * Never commit real secrets to version control.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secretKey,
        long expirationMs,
        long refreshExpirationMs
) {}