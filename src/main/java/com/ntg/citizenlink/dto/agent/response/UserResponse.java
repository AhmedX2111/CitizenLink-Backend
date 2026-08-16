package com.ntg.citizenlink.dto.agent.response;

import com.ntg.citizenlink.enums.UserRole;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Safe user response DTO — passwordHash is deliberately absent.
 * Never expose the AppUser entity directly.
 */
@Getter
@Builder
public class UserResponse {

    private UUID id;
    private String displayRef;      // e.g. "USR-8492-X" derived from ID
    private String username;
    private String displayName;
    private String email;
    private UserRole role;
    private Boolean active;
    private OffsetDateTime createdAt;
}