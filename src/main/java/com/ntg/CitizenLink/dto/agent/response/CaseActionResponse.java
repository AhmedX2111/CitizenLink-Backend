package com.ntg.CitizenLink.dto.agent.response;

import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.WorkflowAction;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A single button the frontend should render for the current user
 * on this case, given its current status (US-17, DET-06).
 *
 * requiresComment / requiresResolutionSummary tell the frontend whether
 * to show an input field before submitting the transition (WFL-03, WFL-04).
 */
@Getter
@AllArgsConstructor
public class CaseActionResponse {

    private WorkflowAction action;
    private String labelKey;          // i18n key, e.g. "cases.actions.assign"
    private CaseStatus resultingStatus;
    private boolean requiresComment;            // true for SUSPEND
    private boolean requiresResolutionSummary;  // true for RESOLVE
}