package com.ntg.CitizenLink.controller;


import com.ntg.CitizenLink.dto.agent.request.CaseSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCaseRequest;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.service.CaseService;
import com.ntg.CitizenLink.dto.agent.response.CaseResponse;
import jakarta.validation.Valid;
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
@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    // =========================================================================
    // POST /api/v1/cases
    // =========================================================================

    /**
     * Creates a new case.
     *
     * Access: AGENT, HANDLER, SUPERVISOR, ADMIN
     *
     * The authenticated user is automatically set as createdByUser.
     * Status is always NEW — any status field in the request body is ignored
     * because it is not present in CreateCaseRequest.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT', 'HANDLER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<CaseResponse> createCase(
            @Valid @RequestBody CreateCaseRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        UUID creatorId = extractUserId(principal);
        CaseResponse response = caseService.createCase(request, creatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // GET /api/v1/cases
    // =========================================================================

    /**
     * Returns a paginated, filtered list of cases created by the authenticated user.
     *
     * Access: AGENT, HANDLER, SUPERVISOR, ADMIN
     *
     * Query parameters (all optional):
     *   status          - CaseStatus enum value
     *   type            - CaseType enum value (COMPLAINT | REQUEST)
     *   priority        - Priority enum value
     *   assignedToUserId - UUID
     *   keyword         - matches caseNumber or subject (case-insensitive)
     *   page            - 0-based page index (default 0)
     *   size            - page size (default 20, max 100)
     *
     * Sorting: createdAt DESC (fixed, not client-configurable in Phase 1)
     *
     * Example:
     *   GET /api/v1/cases?status=NEW&priority=HIGH&keyword=billing&page=0&size=20
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'HANDLER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<PagedResponse<CaseResponse>> searchCases(
            @Valid @ModelAttribute CaseSearchRequest filter,
            @AuthenticationPrincipal UserDetails principal) {

        UUID requesterId = extractUserId(principal);
        PagedResponse<CaseResponse> response = caseService.searchCases(filter, requesterId);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Extracts the user's UUID from the JWT principal.
     *
     * This assumes your UserDetails implementation stores the UUID as the username,
     * OR that you have a custom UserDetails that exposes getId().
     *
     * If your UserDetailsService loads users by UUID stored as username (common pattern),
     * this works as-is. If username is a string (e.g. "john.doe"), adapt this method
     * to look up the AppUser by username from a cache or the security context.
     *
     * IMPORTANT: Replace this with your actual principal extraction if your
     * UserDetails implementation differs. The service layer accepts UUID —
     * the controller is responsible for the translation.
     */
    private UUID extractUserId(UserDetails principal) {
        // Assumes username in UserDetails == UUID of the AppUser.
        // If your JWT stores the UUID as subject, parse it here.
        return UUID.fromString(principal.getUsername());
    }
}
