package com.ntg.CitizenLink.controller;


import com.ntg.CitizenLink.dto.agent.request.CaseSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CaseTransitionRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCaseRequest;
import com.ntg.CitizenLink.dto.agent.response.CaseActionResponse;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.dto.agent.response.StatusHistoryResponse;
import com.ntg.CitizenLink.security.config.SecurityContextHelper;
import com.ntg.CitizenLink.service.interfaces.CaseService;
import com.ntg.CitizenLink.dto.agent.response.CaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for case creation and search.
 *
 * Security model (Phase 1):
 *   Both endpoints require AGENT role or above.
 *   The service layer enforces visibility: AGENT sees only cases they created.
 *
 * The controller never accesses repositories directly.
 * It never returns entity objects — only DTOs from CaseService.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Create a new case.
     * Accessible to all staff roles (AGENT, HANDLER, SUPERVISOR, ADMIN)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT', 'HANDLER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<CaseResponse> createCase(
            @Valid @RequestBody CreateCaseRequest request) {

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        CaseResponse response = caseService.createCase(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Search cases with pagination and filters.
     * Accessible to all staff roles (AGENT, HANDLER, SUPERVISOR, ADMIN)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'HANDLER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<PagedResponse<CaseResponse>> searchCases(
            @Valid CaseSearchRequest request) {

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        PagedResponse<CaseResponse> response = caseService.searchCases(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get case by ID.
     * Accessible to all staff roles (AGENT, HANDLER, SUPERVISOR, ADMIN)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'HANDLER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<CaseResponse> getCaseById(
            @PathVariable UUID id) {

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        CaseResponse response = caseService.getCaseById(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/cases/{id}/timeline
     *
     * US-14, DET-03: full chronological status-history for the case-detail page.
     * Same Phase 1 visibility rule as getCaseById — 404 if not the creator.
     */
    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<StatusHistoryResponse>> getCaseTimeline(@PathVariable UUID id) {
        UUID userId = securityContextHelper.getAuthenticatedUserId();
        List<StatusHistoryResponse> response = caseService.getCaseTimeline(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/cases/{id}/actions
     *
     * US-17, DET-06: returns the workflow actions the current user is
     * permitted to trigger on this case right now. Drives button
     * visibility on the case-detail page.
     */
    @GetMapping("/{id}/actions")
    @PreAuthorize("hasAnyRole('AGENT', 'HANDLER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<List<CaseActionResponse>> getCaseActions(@PathVariable UUID id) {
        UUID userId = securityContextHelper.getAuthenticatedUserId();
        List<CaseActionResponse> response = caseService.getCaseActions(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/cases/{id}/transition
     *
     * WFL-01: executes a workflow transition. Returns 409 Conflict if the
     * transition is illegal for the case's current status or the
     * requester's role (handled by IllegalTransitionException).
     */
    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('HANDLER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<CaseResponse> transitionCase(
            @PathVariable UUID id,
            @Valid @RequestBody CaseTransitionRequest request) {

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        CaseResponse response = caseService.transitionCase(id, userId, request);
        return ResponseEntity.ok(response);
    }
}