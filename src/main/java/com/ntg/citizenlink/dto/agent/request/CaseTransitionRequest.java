package com.ntg.citizenlink.dto.agent.request;

import com.ntg.citizenlink.enums.WorkflowAction;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Request body for POST /api/v1/cases/{id}/transition (US-17, WFL-01).
 *
 * comment        — required when action = SUSPEND (WFL-03)
 * resolutionSummary — required when action = RESOLVE (WFL-04)
 * Both validated in the service layer, not via @NotBlank here, since
 * they're conditionally required depending on the action value.
 */
@Getter
@Setter
public class CaseTransitionRequest {

    @NotNull(message = "Action is required")
    private WorkflowAction action;

    private String comment;
    private String resolutionSummary;

    private UUID assignedToUserId;
}