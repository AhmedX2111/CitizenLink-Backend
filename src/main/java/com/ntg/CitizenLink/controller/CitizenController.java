package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.request.CitizenSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCitizenRequest;
import com.ntg.CitizenLink.dto.agent.response.CitizenProfileResponse;
import com.ntg.CitizenLink.dto.agent.response.CitizenResponse;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import com.ntg.CitizenLink.security.config.SecurityContextHelper;
import com.ntg.CitizenLink.service.interfaces.CitizenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/citizens")
@RequiredArgsConstructor
public class CitizenController {

    private final CitizenService citizenService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * US-07: Search for a citizen
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<PagedResponse<CitizenResponse>> searchCitizens(
            @Valid CitizenSearchRequest request
    ) {
        log.info("GET /api/v1/citizens/search - searchTerm: {}, page: {}, size: {}", request.getSearchTerm(), request.getPage(), request.getSize());

        PagedResponse<CitizenResponse> response = citizenService.searchCitizens(request);

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
     * Get citizen by ID (basic profile)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<CitizenResponse> getCitizenById(@PathVariable UUID id) {
        log.info("GET /api/v1/citizens/{}", id);

        CitizenResponse response = citizenService.getCitizenById(id);

        return ResponseEntity.ok(response);
    }
}