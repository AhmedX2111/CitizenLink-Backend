package com.ntg.CitizenLink.dto.agent.request;

import com.ntg.CitizenLink.enums.UserRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * Query parameters for GET /api/v1/users (US-29).
 * All filters optional — combined with AND logic.
 */
@Getter
@Setter
public class UserSearchRequest {

    private UserRole role;       // filter by role
    private Boolean active;     // filter by active status

    @Min(0)
    private int page = 0;

    @Min(1) @Max(100)
    private int size = 20;
}