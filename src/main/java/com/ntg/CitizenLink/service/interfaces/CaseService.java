package com.ntg.CitizenLink.service.interfaces;

import com.ntg.CitizenLink.dto.agent.request.CaseSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCaseRequest;
import com.ntg.CitizenLink.dto.agent.response.CaseResponse;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
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
}
