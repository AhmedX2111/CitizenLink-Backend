package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.exception.DuplicateResourceException;
import com.ntg.citizenlink.exception.ResourceNotFoundException;
import com.ntg.citizenlink.dto.agent.request.CitizenSearchRequest;
import com.ntg.citizenlink.dto.agent.request.CreateCitizenRequest;
import com.ntg.citizenlink.dto.agent.response.CaseSummaryResponse;
import com.ntg.citizenlink.dto.agent.response.CitizenProfileResponse;
import com.ntg.citizenlink.dto.agent.response.CitizenResponse;
import com.ntg.citizenlink.dto.agent.response.PagedResponse;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.CaseRepository;
import com.ntg.citizenlink.repositories.CitizenRepository;
import com.ntg.citizenlink.service.interfaces.CitizenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CitizenServiceImpl implements CitizenService {

    private final CitizenRepository citizenRepository;
    private final AppUserRepository appUserRepository;
    private final CaseRepository caseRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CitizenResponse> searchCitizens(CitizenSearchRequest request) {
        // M-15: the search term may be a national ID or phone number. Log only
        // presence + length, never the identifier itself.
        String searchTerm = request.getSearchTerm();
        log.info("Searching citizens - termPresent: {}, termLength: {}",
                searchTerm != null && !searchTerm.isBlank(),
                searchTerm != null ? searchTerm.length() : 0);

        if (request.isEmpty()) {
            log.warn("Empty search term provided");
            return new PagedResponse<>(List.of(), 0, request.getSize(), 0, 0);
        }

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        Page<Citizen> citizenPage = citizenRepository.searchCitizens(
                request.getSearchTerm().trim(),
                pageable
        );

        // ── Fetch all case counts for this page in ONE query ──────────────
        List<UUID> citizenIds = citizenPage.getContent()
                .stream()
                .map(Citizen::getId)
                .collect(Collectors.toList());

        Map<UUID, Long> caseCountsById = citizenRepository
                .countCasesByCitizenIds(citizenIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]
                ));

        List<CitizenResponse> content = citizenPage.getContent()
                .stream()
                .map(c -> toResponse(c, caseCountsById.getOrDefault(c.getId(), 0L)))
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                citizenPage.getNumber(),
                citizenPage.getSize(),
                citizenPage.getTotalElements(),
                citizenPage.getTotalPages()
        );
    }

    @Override
    @Transactional
    public CitizenResponse createCitizen(CreateCitizenRequest request, UUID createdByUserId) {
        log.info("Creating new citizen");

        // Check for duplicate national ID
        if (citizenRepository.existsByNationalId(request.getNationalId())) {
            log.warn("Citizen creation failed due to duplicate national ID");
            throw new DuplicateResourceException("Citizen", "national ID", request.getNationalId());
        }

        // Check for duplicate phone
        if (citizenRepository.existsByPhone(request.getPhone())) {
            log.warn("Duplicate phone number detected during citizen creation");
            throw new DuplicateResourceException("Citizen", "phone number", request.getPhone());
        }

        // Process email: convert empty string to null
        String email = request.getEmail();
        if (email != null && email.trim().isEmpty()) {
            email = null;
        }

        // Check for duplicate email (only if email is not null)
        if (email != null && citizenRepository.existsByEmail(email)) {
            log.warn("Duplicate email detected during citizen creation");
            throw new DuplicateResourceException("Citizen", "email", email);
        }

        // Get the creating user
        AppUser createdBy = appUserRepository.findById(createdByUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", createdByUserId));

        // Create new citizen entity
        Citizen citizen = new Citizen();
        citizen.setFullName(request.getFullName());
        citizen.setNationalId(request.getNationalId());
        citizen.setPhone(request.getPhone());
        citizen.setEmail(email);
        citizen.setPreferredLanguage(request.getPreferredLanguage() != null ? request.getPreferredLanguage() : "en");
        citizen.setCreatedByUser(createdBy);

        Citizen savedCitizen = citizenRepository.save(citizen);

        log.info("Citizen created successfully with ID: {}", savedCitizen.getId());

        // Brand new citizen — guaranteed to have zero cases, no query needed.
        return toResponse(savedCitizen, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public CitizenProfileResponse getCitizenProfile(UUID id, UUID requesterId) {
        log.info("Fetching citizen profile for ID: {} by user: {}", id, requesterId);

        Citizen citizen = citizenRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Citizen", id));

        AppUser requester = appUserRepository.findById(requesterId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", requesterId));

        // M-17: push the visibility rule into the queries instead of fetching
        // every case and filtering in memory. Same role->filter mapping as
        // CaseServiceImpl.searchCases.
        UUID createdByFilter = null;
        UUID assignedToFilter = null;

        switch (requester.getRole()) {
            case ADMIN:
            case SUPERVISOR:
                // both null — see all of the citizen's cases
                break;
            case HANDLER:
                assignedToFilter = requesterId;
                break;
            default: // AGENT
                createdByFilter = requesterId;
                break;
        }

        Map<CaseStatus, Long> countsByStatus = caseRepository
                .countVisibleByCitizenIdByStatus(id, createdByFilter, assignedToFilter)
                .stream()
                .collect(Collectors.toMap(
                        row -> (CaseStatus) row[0],
                        row -> (Long) row[1]
                ));

        long totalCases = countsByStatus.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long openCases = countsByStatus.entrySet().stream()
                .filter(e -> e.getKey() != CaseStatus.RESOLVED
                        && e.getKey() != CaseStatus.CLOSED
                        && e.getKey() != CaseStatus.CANCELLED)
                .mapToLong(e -> e.getValue())
                .sum();
        long resolvedCases = countsByStatus.entrySet().stream()
                .filter(e -> e.getKey() == CaseStatus.RESOLVED
                        || e.getKey() == CaseStatus.CLOSED)
                .mapToLong(e -> e.getValue())
                .sum();

        List<Case> recentCases = caseRepository
                .findVisibleByCitizenIdOrderByCreatedAtDesc(
                        id, createdByFilter, assignedToFilter, PageRequest.of(0, 5));

        return CitizenProfileResponse.builder()
                .id(citizen.getId())
                .fullName(citizen.getFullName())
                .nationalId(citizen.getNationalId())
                .phone(citizen.getPhone())
                .email(citizen.getEmail())
                .preferredLanguage(citizen.getPreferredLanguage())
                .createdAt(citizen.getCreatedAt())
                .createdByUserName(citizen.getCreatedByUser() != null ?
                        citizen.getCreatedByUser().getDisplayName() : null)
                .totalCases((int) totalCases)
                .openCases((int) openCases)
                .resolvedCases((int) resolvedCases)
                .recentCases(recentCases.stream()
                        .map(this::toCaseSummary)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CitizenResponse getCitizenById(UUID id) {
        log.info("Fetching citizen by ID: {}", id);

        Citizen citizen = citizenRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Citizen", id));

        long caseCount = caseRepository.countByCitizenId(id);

        return toResponse(citizen, caseCount);
    }

    @Override
    public boolean existsByNationalId(String nationalId) {
        return citizenRepository.existsByNationalId(nationalId);
    }

    /**
     * Convert Citizen entity to CitizenResponse DTO.
     *
     * caseCount is always passed in by the caller rather than looked up here —
     * this keeps the mapper a pure function with no hidden database calls,
     * and lets callers batch-fetch counts (e.g. searchCitizens uses one grouped
     * query for an entire page instead of one query per citizen).
     */
    private CitizenResponse toResponse(Citizen citizen, long caseCount) {
        return CitizenResponse.builder()
                .id(citizen.getId())
                .fullName(citizen.getFullName())
                .nationalId(citizen.getNationalId())
                .phone(citizen.getPhone())
                .email(citizen.getEmail())
                .preferredLanguage(citizen.getPreferredLanguage())
                .createdAt(citizen.getCreatedAt())
                .caseCount((int) caseCount)
                .build();
    }

    /**
     * Convert Case entity to CaseSummaryResponse DTO
     */
    private CaseSummaryResponse toCaseSummary(Case caseEntity) {
        return CaseSummaryResponse.builder()
                .id(caseEntity.getId())
                .caseNumber(caseEntity.getCaseNumber())
                .subject(caseEntity.getSubject())
                .status(caseEntity.getStatus() != null ? caseEntity.getStatus().name() : null)
                .priority(caseEntity.getPriority() != null ? caseEntity.getPriority().name() : null)
                .createdAt(caseEntity.getCreatedAt())
                .assignedToName(caseEntity.getAssignedToUser() != null ?
                        caseEntity.getAssignedToUser().getDisplayName() : null)
                .build();
    }
}