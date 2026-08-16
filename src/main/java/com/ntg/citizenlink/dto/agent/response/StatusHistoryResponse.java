package com.ntg.citizenlink.dto.agent.response;

import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.WorkflowAction;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single status-history entry (US-14, DET-03).
 *
 * fromStatus is null on the initial CREATE event — the frontend must
 * render this as "Case created" rather than "X -> Y" in that one case.
 */
@Getter
@Builder
public class StatusHistoryResponse {

    private UUID id;
    private CaseStatus fromStatus;   // null on creation event
    private CaseStatus toStatus;
    private WorkflowAction action;
    private String comment;
    private OffsetDateTime createdAt;

    // Flattened user ref — same pattern as CaseResponse
    private UUID changedByUserId;
    private String changedByDisplayName;
}