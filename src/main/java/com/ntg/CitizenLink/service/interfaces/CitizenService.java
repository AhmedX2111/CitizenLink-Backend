package com.ntg.CitizenLink.service.interfaces;

import com.ntg.CitizenLink.dto.agent.request.CitizenSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCitizenRequest;
import com.ntg.CitizenLink.dto.agent.response.CitizenProfileResponse;
import com.ntg.CitizenLink.dto.agent.response.CitizenResponse;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
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
     */
    CitizenProfileResponse getCitizenProfile(UUID id);

    /**
     * Get citizen by ID with case count.
     */
    CitizenResponse getCitizenById(UUID id);

    /**
     * Check if citizen exists by national ID.
     */
    boolean existsByNationalId(String nationalId);
}
