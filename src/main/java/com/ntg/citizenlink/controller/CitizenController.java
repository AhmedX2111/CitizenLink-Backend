package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.dto.agent.request.CitizenSearchRequest;
import com.ntg.citizenlink.dto.agent.request.CreateCitizenCaseRequest;
import com.ntg.citizenlink.dto.agent.request.CreateCitizenRequest;
import com.ntg.citizenlink.dto.agent.response.CaseResponse;
import com.ntg.citizenlink.dto.agent.response.CaseSummaryResponse;
import com.ntg.citizenlink.dto.agent.response.CitizenProfileResponse;
import com.ntg.citizenlink.dto.agent.response.CitizenResponse;
import com.ntg.citizenlink.dto.agent.response.DuplicateCaseCandidateResponse;
import com.ntg.citizenlink.dto.agent.response.PagedResponse;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.security.config.SecurityContextHelper;
import com.ntg.citizenlink.service.interfaces.CaseService;
import com.ntg.citizenlink.service.interfaces.CitizenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/citizens")
@RequiredArgsConstructor
public class CitizenController {

    private final CitizenService citizenService;
    private final CaseService caseService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * US-07: Search for a citizen
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<PagedResponse<CitizenResponse>> searchCitizens(
            @Valid CitizenSearchRequest request
    ) {
        // M-15: the search term may be a national ID or phone number — a
        // government identifier. Never log the raw value; log only whether one
        // was supplied and its length so traffic stays correlatable and sized
        // without exposing the identifier (logs/citizenlink.json, 30-day ret.).
        String searchTerm = request.getSearchTerm();
        log.info("GET /api/v1/citizens/search - termPresent: {}, termLength: {}, page: {}, size: {}",
                searchTerm != null && !searchTerm.isBlank(),
                searchTerm != null ? searchTerm.length() : 0,
                request.getPage(), request.getSize());

        UUID userId = securityContextHelper.getAuthenticatedUserId();

        PagedResponse<CitizenResponse> response = citizenService.searchCitizens(request, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * US-09: Create a new citizen record
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<CitizenResponse> createCitizen(
            @Valid @RequestBody CreateCitizenRequest request
    ) {
        UUID userId = securityContextHelper.getAuthenticatedUserId();

        log.info("POST /api/v1/citizens - createdBy: {}",
                securityContextHelper.getAuthenticatedUsername());

        CitizenResponse response = citizenService.createCitizen(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * US-08: Get citizen 360 profile with case history
     */
    @GetMapping("/profile/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<CitizenProfileResponse> getCitizen360(@PathVariable UUID id) {
        log.info("GET /api/v1/citizens/{}/profile", id);

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        CitizenProfileResponse response = citizenService.getCitizenProfile(id, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * US-57: create a case directly from the Citizen 360 screen.
     * The citizen is bound to the URL path so the agent's citizen choice
     * is locked server-side; the (possibly masked) national ID is never
     * needed in the request body (US-56).
     */
    @PostMapping("/{id}/cases")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<CaseResponse> createCaseForCitizen(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCitizenCaseRequest request) {
        log.info("POST /api/v1/citizens/{}/cases - createdBy: {}",
                id, securityContextHelper.getAuthenticatedUsername());

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        CaseResponse response = caseService.createCitizenCase(id, request, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * US-59: page through a citizen's complete permitted case history, ordered
     * by last update (updatedAt DESC). The same role->visibility restriction
     * as the 360 profile Recent Cases list applies, so this endpoint never
     * returns a case the requester could not see in the profile.
     */
    @GetMapping("/{id}/cases")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<PagedResponse<CaseSummaryResponse>> getCitizenCaseHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        log.info("GET /api/v1/citizens/{}/cases - requester: {}, page: {}, size: {}",
                id, securityContextHelper.getAuthenticatedUsername(), page, size);

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        PagedResponse<CaseSummaryResponse> response =
                citizenService.getCitizenCaseHistory(id, userId, page, size);

        return ResponseEntity.ok(response);
    }

    /**
     * US-58: possible-duplicate preflight before creating a case from the
     * Citizen 360 screen. Read-only and non-blocking — the caller only uses
     * the result to decide whether to warn the agent; creation itself is
     * never gated server-side.
     */
    @GetMapping("/{id}/cases/duplicate-candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<List<DuplicateCaseCandidateResponse>> findDuplicateCandidates(
            @PathVariable UUID id,
            @RequestParam UUID categoryId,
            @RequestParam UUID departmentId) {
        log.info("GET /api/v1/citizens/{}/cases/duplicate-candidates - requester: {}",
                id, securityContextHelper.getAuthenticatedUsername());

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        List<DuplicateCaseCandidateResponse> candidates =
                caseService.findDuplicateCandidates(id, categoryId, departmentId, userId);

        return ResponseEntity.ok(candidates);
    }

    /**
     * Get citizen by ID (basic profile)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<CitizenResponse> getCitizenById(@PathVariable UUID id) {
        log.info("GET /api/v1/citizens/{}", id);

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        CitizenResponse response = citizenService.getCitizenById(id, userId);

        return ResponseEntity.ok(response);
    }
}