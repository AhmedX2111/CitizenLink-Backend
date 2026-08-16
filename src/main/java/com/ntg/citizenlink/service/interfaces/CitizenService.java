package com.ntg.citizenlink.service.interfaces;

import com.ntg.citizenlink.dto.agent.request.CitizenSearchRequest;
import com.ntg.citizenlink.dto.agent.request.CreateCitizenRequest;
import com.ntg.citizenlink.dto.agent.response.CitizenProfileResponse;
import com.ntg.citizenlink.dto.agent.response.CitizenResponse;
import com.ntg.citizenlink.dto.agent.response.PagedResponse;
import org.springframework.data.domain.Page;
import java.util.UUID;


public interface CitizenService {

    /**
     * Search citizens by name (partial), national ID, or phone.
     * Returns paginated response with PagedResponse wrapper.
     */
    PagedResponse<CitizenResponse> searchCitizens(CitizenSearchRequest request);

    /**
     * Create a new citizen record.
     */
    CitizenResponse createCitizen(CreateCitizenRequest request, UUID createdByUserId);

    /**
     * Get citizen profile with case history (Citizen 360).
     * Cases are filtered through CaseAccessPolicy for the requesting user.
     */
    CitizenProfileResponse getCitizenProfile(UUID id, UUID requesterId);

    /**
     * Get citizen by ID with case count.
     */
    CitizenResponse getCitizenById(UUID id);

    /**
     * Check if citizen exists by national ID.
     */
    boolean existsByNationalId(String nationalId);
}
