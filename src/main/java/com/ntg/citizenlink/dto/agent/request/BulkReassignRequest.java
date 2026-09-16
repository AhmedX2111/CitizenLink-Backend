package com.ntg.citizenlink.dto.agent.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Request body for POST /api/v1/cases/bulk-reassign (US-53).
 *
 * caseIds           — IDs of the cases to move to the new handler.
 *                     Duplicates are collapsed; each requested ID gets an
 *                     explicit per-case result in the response.
 * assignedToUserId  — destination user; must be an ACTIVE HANDLER
 *                     (the UI populates this from GET /api/v1/users/handlers,
 *                     which already lists active HANDLER users only).
 * comment           — reason, required for accountability (mirrors the
 *                     single-case REASSIGN rule, which requires a comment).
 */
@Getter
@Setter
public class BulkReassignRequest {

    @NotEmpty(message = "At least one case ID is required")
    private List<UUID> caseIds;

    @NotNull(message = "assignedToUserId is required")
    private UUID assignedToUserId;

    @NotBlank(message = "A comment/reason is required for reassignment")
    @Size(max = 5000, message = "Comment must not exceed 5000 characters")
    private String comment;
}
