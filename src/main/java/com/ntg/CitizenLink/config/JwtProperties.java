package com.ntg.CitizenLink.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the jwt.* block from application.yml.
 *
 * application.yml must contain:
 *   jwt:
 *     secret-key: <base64-encoded 256-bit key>
 *     expiration-ms: 86400000   # 24 hours
 *
 * Generate a safe secret once:
 *   openssl rand -base64 32
 * Never commit a real secret to version control — use env vars or Vault in prod.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secretKey,
        long expirationMs
) {}
