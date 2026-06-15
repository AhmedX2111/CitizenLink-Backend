package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.request.CitizenSearchRequest;
import com.ntg.CitizenLink.dto.agent.response.CitizenResponse;
import com.ntg.CitizenLink.dto.agent.response.CitizenSearchResponse;
import com.ntg.CitizenLink.service.CitizenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/citizens")
@RequiredArgsConstructor
public class CitizenController {

    private final CitizenService citizenService;

    /**
     * US-07: Search for a citizen
     * Search by national ID, phone number, or partial name
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<CitizenSearchResponse<CitizenResponse>> searchCitizens(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("REST request: GET /api/v1/citizens/search - searchTerm: {}, page: {}, size: {}", searchTerm, page, size);

        CitizenSearchRequest request = new CitizenSearchRequest();
        request.setSearchTerm(searchTerm);
        request.setPage(page);
        request.setSize(size);

        CitizenSearchResponse<CitizenResponse> response = citizenService.searchCitizens(request);

        log.info("REST response: GET /api/v1/citizens/search - returned {} citizens",
                response.getContent() != null ? response.getContent().size() : 0);

        return ResponseEntity.ok(response);
    }

    /**
     * Get citizen by ID (for Citizen 360 - US-08)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<CitizenResponse> getCitizenById(@PathVariable UUID id) {
        log.info("REST request: GET /api/v1/citizens/{}", id);

        CitizenResponse response = citizenService.getCitizenById(id);

        log.info("REST response: GET /api/v1/citizens/{} - citizen found", id);
        return ResponseEntity.ok(response);
    }
}
