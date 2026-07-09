package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.GEH.IllegalTransitionException;
import com.ntg.CitizenLink.dto.agent.response.CaseActionResponse;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.UserRole;
import com.ntg.CitizenLink.enums.WorkflowAction;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Encodes the case workflow state machine from BRD §5.5.2 (allowed
 * transitions) and §5.5.3 (which roles can press which button).
 *
 * This is the SINGLE SOURCE OF TRUTH for the workflow. Both the
 * "what buttons should I show" endpoint (US-17) and the "execute this
 * transition" endpoint read from the exact same table below — there is
 * no second copy of these rules anywhere else in the backend.
 *
 * Table source: BRD §5.5.3 "Actions by button (UI)"
 *   Action        | From status(es)          | To status     | Roles
 *   Assign        | NEW                      | ASSIGNED      | SUPERVISOR, ADMIN
 *   Start work    | ASSIGNED                 | IN_PROGRESS   | HANDLER, SUPERVISOR
 *   Request info  | IN_PROGRESS              | AWAITING_INFO | HANDLER, SUPERVISOR
 *   Resume        | AWAITING_INFO, SUSPENDED | IN_PROGRESS   | HANDLER, SUPERVISOR
 *   Suspend       | IN_PROGRESS              | SUSPENDED     | HANDLER, SUPERVISOR
 *   Resolve       | IN_PROGRESS              | RESOLVED      | HANDLER, SUPERVISOR
 *   Close         | RESOLVED                 | CLOSED        | SUPERVISOR, ADMIN
 *   Reopen        | RESOLVED, CLOSED         | IN_PROGRESS   | SUPERVISOR
 *   Cancel        | NEW, ASSIGNED            | CANCELLED     | SUPERVISOR, ADMIN
 *
 * Note: "Resume" is split into two distinct WorkflowAction values because
 * the underlying action differs by source status (BRD §5.5.2 diagram):
 *   AWAITING_INFO -> IN_PROGRESS  is action INFO_RECEIVED
 *   SUSPENDED     -> IN_PROGRESS  is action RESUME
 * Both share the same UI button label ("Resume") but are tracked as
 * separate WorkflowAction enum values for an accurate audit trail.
 */
@Service
public class CaseWorkflowService {


    private static final List<CaseTransitionRule> RULES = List.of(

            new CaseTransitionRule(
                    WorkflowAction.ASSIGN, CaseStatus.NEW, CaseStatus.ASSIGNED,
                    Set.of(UserRole.SUPERVISOR, UserRole.ADMIN),
                    "cases.actions.assign", false, false),

            new CaseTransitionRule(
                    WorkflowAction.START, CaseStatus.ASSIGNED, CaseStatus.IN_PROGRESS,
                    Set.of(UserRole.HANDLER, UserRole.SUPERVISOR),
                    "cases.actions.start", false, false),

            new CaseTransitionRule(
                    WorkflowAction.AWAIT_INFO, CaseStatus.IN_PROGRESS, CaseStatus.AWAITING_INFO,
                    Set.of(UserRole.HANDLER, UserRole.SUPERVISOR),
                    "cases.actions.requestInfo", false, false),

            // "Resume" — two source statuses, two distinct actions, same label
            new CaseTransitionRule(
                    WorkflowAction.INFO_RECEIVED, CaseStatus.AWAITING_INFO, CaseStatus.IN_PROGRESS,
                    Set.of(UserRole.HANDLER, UserRole.SUPERVISOR),
                    "cases.actions.resume", false, false),

            new CaseTransitionRule(
                    WorkflowAction.RESUME, CaseStatus.SUSPENDED, CaseStatus.IN_PROGRESS,
                    Set.of(UserRole.HANDLER, UserRole.SUPERVISOR),
                    "cases.actions.resume", false, false),

            new CaseTransitionRule(
                    WorkflowAction.SUSPEND, CaseStatus.IN_PROGRESS, CaseStatus.SUSPENDED,
                    Set.of(UserRole.HANDLER, UserRole.SUPERVISOR),
                    "cases.actions.suspend", true, false),   // WFL-03: reason required

            new CaseTransitionRule(
                    WorkflowAction.RESOLVE, CaseStatus.IN_PROGRESS, CaseStatus.RESOLVED,
                    Set.of(UserRole.HANDLER, UserRole.SUPERVISOR),
                    "cases.actions.resolve", false, true),   // WFL-04: resolutionSummary required

            new CaseTransitionRule(
                    WorkflowAction.CLOSE, CaseStatus.RESOLVED, CaseStatus.CLOSED,
                    Set.of(UserRole.SUPERVISOR, UserRole.ADMIN),
                    "cases.actions.close", false, false),

            // "Reopen" — two source statuses, same action and target
            new CaseTransitionRule(
                    WorkflowAction.REOPEN, CaseStatus.RESOLVED, CaseStatus.IN_PROGRESS,
                    Set.of(UserRole.SUPERVISOR),
                    "cases.actions.reopen", false, false),

            new CaseTransitionRule(
                    WorkflowAction.REOPEN, CaseStatus.CLOSED, CaseStatus.IN_PROGRESS,
                    Set.of(UserRole.SUPERVISOR),
                    "cases.actions.reopen", false, false),

            // "Cancel" — two source statuses, same action and target
            new CaseTransitionRule(
                    WorkflowAction.CANCEL, CaseStatus.NEW, CaseStatus.CANCELLED,
                    Set.of(UserRole.SUPERVISOR, UserRole.ADMIN),
                    "cases.actions.cancel", false, false),

            new CaseTransitionRule(
                    WorkflowAction.CANCEL, CaseStatus.ASSIGNED, CaseStatus.CANCELLED,
                    Set.of(UserRole.SUPERVISOR, UserRole.ADMIN),
                    "cases.actions.cancel", false, false)
    );

    /**
     * US-17: returns every action this user is permitted to trigger on
     * this case, given its CURRENT status. Used by the GET /actions
     * endpoint to drive button visibility.
     *
     * Important: this does NOT yet check the HANDLER-must-be-assignee
     * rule — that's an ownership check, separate from the role+status
     * check done here. CaseServiceImpl combines both before returning
     * results, since this class has no knowledge of who's assigned to
     * the case beyond what's passed to it via currentUserRole.
     */
    public List<CaseActionResponse> getAllowedActions(CaseStatus currentStatus, UserRole currentUserRole) {
        return RULES.stream()
                .filter(rule -> rule.fromStatus() == currentStatus)
                .filter(rule -> rule.allowedRoles().contains(currentUserRole))
                .map(rule -> new CaseActionResponse(
                        rule.action(),
                        rule.labelKey(),
                        rule.toStatus(),
                        rule.requiresComment(),
                        rule.requiresResolutionSummary()
                ))
                .toList();
    }

    /**
     * Validates and resolves a single transition request.
     * Throws IllegalTransitionException (-> 409, WFL-01) if:
     *   - no rule exists for (currentStatus, action)
     *   - the rule exists but currentUserRole is not in allowedRoles
     *
     * Returns the matched rule so the caller can read toStatus and the
     * requiresComment/requiresResolutionSummary flags for validation.
     */
    public CaseTransitionRule resolveTransition(CaseStatus currentStatus, WorkflowAction action, UserRole currentUserRole) {
                CaseTransitionRule match = RULES.stream()
                .filter(rule -> rule.fromStatus() == currentStatus && rule.action() == action)
                .findFirst()
                .orElseThrow(() -> new IllegalTransitionException("INVALID_TRANSITION",
                        "Action " + action + " is not valid from status " + currentStatus));

        if (!match.allowedRoles().contains(currentUserRole)) {
            throw new IllegalTransitionException("ROLE_NOT_ALLOWED",
                    "Role " + currentUserRole + " is not permitted to perform action " + action);
        }

        return match;
    }
}