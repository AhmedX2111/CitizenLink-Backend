package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.GEH.DuplicateResourceException;
import com.ntg.CitizenLink.GEH.ResourceNotFoundException;
import com.ntg.CitizenLink.dto.agent.request.CitizenSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCitizenRequest;
import com.ntg.CitizenLink.dto.agent.response.CaseSummaryResponse;
import com.ntg.CitizenLink.dto.agent.response.CitizenProfileResponse;
import com.ntg.CitizenLink.dto.agent.response.CitizenResponse;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.entities.Citizen;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import com.ntg.CitizenLink.repositories.CaseRepository;
import com.ntg.CitizenLink.repositories.CitizenRepository;
import com.ntg.CitizenLink.service.interfaces.CitizenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
        log.info("Searching citizens with term: '{}'", request.getSearchTerm());

        if (request.isEmpty()) {
            log.warn("Empty search term provided");
            return new PagedResponse<>(List.of(), 0, request.getSize(), 0, 0);
        }

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        Page<Citizen> citizenPage = citizenRepository.searchCitizens(
                request.getSearchTerm().trim(),
                pageable
        );

        List<CitizenResponse> content = citizenPage.getContent()
                .stream()
                .map(this::toResponse)
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
        log.info("Creating new citizen with national ID: {}", request.getNationalId());

        // Check for duplicate national ID
        if (citizenRepository.existsByNationalId(request.getNationalId())) {
            log.warn("Duplicate national ID: {}", request.getNationalId());
            throw new DuplicateResourceException("Citizen", "national ID", request.getNationalId());
        }

        // Check for duplicate phone
        if (citizenRepository.existsByPhone(request.getPhone())) {
            log.warn("Duplicate phone number: {}", request.getPhone());
            throw new DuplicateResourceException("Citizen", "phone number", request.getPhone());
        }

        // Process email: convert empty string to null
        String email = request.getEmail();
        if (email != null && email.trim().isEmpty()) {
            email = null;
        }

        // Check for duplicate email (only if email is not null)
        if (email != null && citizenRepository.existsByEmail(email)) {
            log.warn("Duplicate email: {}", email);
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

        return toResponse(savedCitizen);
    }

    @Override
    @Transactional(readOnly = true)
    public CitizenProfileResponse getCitizenProfile(UUID id) {
        log.info("Fetching citizen profile for ID: {}", id);

        Citizen citizen = citizenRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Citizen", id));

        long totalCases = caseRepository.countByCitizenId(id);

        long openCases = caseRepository.countByCitizenIdAndStatusNotIn(id,
                List.of(CaseStatus.RESOLVED, CaseStatus.CLOSED, CaseStatus.CANCELLED));

        long resolvedCases = caseRepository.countByCitizenIdAndStatusIn(id,
                List.of(CaseStatus.RESOLVED, CaseStatus.CLOSED));

        List<Case> recentCases = caseRepository.findTop5ByCitizenIdOrderByCreatedAtDesc(id);

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

        return toResponse(citizen);
    }

    @Override
    public boolean existsByNationalId(String nationalId) {
        return citizenRepository.existsByNationalId(nationalId);
    }

    /**
     * Convert Citizen entity to CitizenResponse DTO
     */
    private CitizenResponse toResponse(Citizen citizen) {
        long caseCount = citizenRepository.countCasesByCitizenId(citizen.getId());

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
