package com.ntg.CitizenLink.service.interfaces;

import com.ntg.CitizenLink.dto.agent.request.CaseSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCaseRequest;
import com.ntg.CitizenLink.dto.agent.response.CaseResponse;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.dto.agent.response.StatusHistoryResponse;

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
     * Enforces Phase 1 visibility: only the case creator may view it.
     * Throws ResourceNotFoundException (404) if the case doesn't exist
     * OR belongs to a different user — existence is never revealed to
     * unauthorized callers.
     */
    CaseResponse getCaseById(UUID caseId, UUID requesterId);

    /**
     * Returns the full chronological status-history timeline for a case
     * (US-14, DET-03). Enforces the same Phase 1 visibility rule as
     * getCaseById: only the case creator may view it.
     */
    List<StatusHistoryResponse> getCaseTimeline(UUID caseId, UUID requesterId);
}
