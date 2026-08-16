package com.ntg.citizenlink.service.interfaces;

import com.ntg.citizenlink.dto.agent.request.CaseSearchRequest;
import com.ntg.citizenlink.dto.agent.request.CaseTransitionRequest;
import com.ntg.citizenlink.dto.agent.request.CreateCaseRequest;
import com.ntg.citizenlink.dto.agent.response.CaseActionResponse;
import com.ntg.citizenlink.dto.agent.response.CaseResponse;
import com.ntg.citizenlink.dto.agent.response.PagedResponse;
import com.ntg.citizenlink.dto.agent.response.StatusHistoryResponse;

import java.util.List;
import java.util.UUID;


public interface CaseService {

    /**
     * Creates a new case.
     */
    CaseResponse createCase(CreateCaseRequest request, UUID creatorId);

    /**
     * Returns a paginated, filtered list of cases.
     */
    PagedResponse<CaseResponse> searchCases(CaseSearchRequest filter, UUID createdByUserId);

    /**
     * Returns full case details by ID.
     * Enforces visibility via CaseAccessPolicy: ADMIN/SUPERVISOR see any
     * case, HANDLER sees cases assigned to them, AGENT sees cases they
     * created (US-17 visibility update).
     * Throws ResourceNotFoundException (404) if the case doesn't exist
     * OR belongs to a different user — existence is never revealed to
     * unauthorized callers.
     */
    CaseResponse getCaseById(UUID caseId, UUID requesterId);

    /**
     * Returns the full chronological status-history timeline for a case
     * (US-14, DET-03). Enforces the same CaseAccessPolicy visibility rule
     * as getCaseById.
     */
    List<StatusHistoryResponse> getCaseTimeline(UUID caseId, UUID requesterId);

    /**
     * US-17: returns every workflow action the requester is currently
     * permitted to trigger on this case, given its current status and
     * their role + assignment relationship to the case.
     * Drives button visibility on the case-detail page.
     */
    List<CaseActionResponse> getCaseActions(UUID caseId, UUID requesterId);

    /**
     * WFL-01: executes a workflow transition. Validates the action is
     * legal for the case's current status AND the requester's role,
     * throwing IllegalTransitionException (409) if not. Also enforces
     * the HANDLER-must-be-assignee ownership rule.
     *
     * On success: updates Case.status (and resolvedAt/closedAt if
     * applicable per existing entity semantics), writes a StatusHistory
     * row (WFL-02), and returns the updated CaseResponse.
     */
    CaseResponse transitionCase(UUID caseId, UUID requesterId, CaseTransitionRequest request);

}
