package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.dto.agent.request.CitizenSearchRequest;
import com.ntg.CitizenLink.dto.agent.response.CitizenResponse;
import com.ntg.CitizenLink.dto.agent.response.CitizenSearchResponse;
import com.ntg.CitizenLink.entities.Citizen;
import com.ntg.CitizenLink.repositories.CitizenRepository;
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
public class CitizenService {

    private final CitizenRepository citizenRepository;

    /**
     * Search citizens by name (partial), national ID, or phone
     * Returns paginated results
     */
    @Transactional(readOnly = true)
    public CitizenSearchResponse<CitizenResponse> searchCitizens(CitizenSearchRequest request) {
        log.info("Searching citizens with term: '{}'", request.getSearchTerm());

        // Validate search term
        if (request.isEmpty()) {
            log.warn("Empty search term provided");
            return new CitizenSearchResponse<>(
                    List.of(),
                    0,
                    request.getSize(),
                    0,
                    0,
                    true,
                    true
            );
        }

        // Create pageable
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        // Execute search
        Page<Citizen> citizenPage = citizenRepository.searchCitizens(
                request.getSearchTerm().trim(),
                pageable
        );

        // Convert to response DTO
        List<CitizenResponse> content = citizenPage.getContent()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        log.info("Found {} citizens for search term: '{}'", citizenPage.getTotalElements(), request.getSearchTerm());

        return new CitizenSearchResponse<>(
                content,
                citizenPage.getNumber(),
                citizenPage.getSize(),
                citizenPage.getTotalElements(),
                citizenPage.getTotalPages(),
                citizenPage.isFirst(),
                citizenPage.isLast()
        );
    }

    /**
     * Get citizen by ID with case count
     */
    @Transactional(readOnly = true)
    public CitizenResponse getCitizenById(UUID id) {
        log.info("Fetching citizen by ID: {}", id);

        Citizen citizen = citizenRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Citizen not found with id: " + id));

        return toResponse(citizen);
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
}
