package com.ntg.CitizenLink.controller;


import com.ntg.CitizenLink.dto.agent.request.CaseSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCaseRequest;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
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
}