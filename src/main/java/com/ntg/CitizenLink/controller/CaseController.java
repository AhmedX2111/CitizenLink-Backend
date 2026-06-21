package com.ntg.CitizenLink.controller;


import com.ntg.CitizenLink.dto.agent.request.CaseSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCaseRequest;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import com.ntg.CitizenLink.service.interfaces.CaseService;
import com.ntg.CitizenLink.dto.agent.response.CaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;
    private final AppUserRepository appUserRepository;

    @PostMapping
    public ResponseEntity<CaseResponse> createCase(
            @Valid @RequestBody CreateCaseRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = getUserIdFromUsername(userDetails.getUsername());
        CaseResponse response = caseService.createCase(request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<CaseResponse>> searchCases(
            @Valid CaseSearchRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = getUserIdFromUsername(userDetails.getUsername());
        PagedResponse<CaseResponse> response = caseService.searchCases(request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseResponse> getCaseById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = getUserIdFromUsername(userDetails.getUsername());
        CaseResponse response = caseService.getCaseById(id, userId);
        return ResponseEntity.ok(response);
    }

    private UUID getUserIdFromUsername(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return user.getId();
    }
}