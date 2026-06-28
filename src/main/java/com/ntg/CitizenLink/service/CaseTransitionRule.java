package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.UserRole;
import com.ntg.CitizenLink.enums.WorkflowAction;

import java.util.Set;

/**
 * One row of the case workflow transition table (BRD §5.5.2 / §5.5.3).
 * Extracted as a top-level record (not nested inside CaseWorkflowService)
 * so its accessors are visible to CaseServiceImpl, which reads
 * toStatus()/requiresComment()/requiresResolutionSummary() after
 * resolveTransition() returns a match.
 */
public record CaseTransitionRule(
        WorkflowAction action,
        CaseStatus fromStatus,
        CaseStatus toStatus,
        Set<UserRole> allowedRoles,
        String labelKey,
        boolean requiresComment,
        boolean requiresResolutionSummary
) {}