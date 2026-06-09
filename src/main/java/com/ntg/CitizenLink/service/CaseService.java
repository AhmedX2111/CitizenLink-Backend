package com.ntg.CitizenLink.service;


import com.ntg.CitizenLink.GEH.ResourceNotFoundException;
import com.ntg.CitizenLink.dto.agent.request.CaseSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCaseRequest;
import com.ntg.CitizenLink.dto.agent.response.CaseResponse;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.entities.*;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.WorkflowAction;
import com.ntg.CitizenLink.repositories.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CaseService {

    private final CaseRepository caseRepository;
    private final CitizenRepository citizenRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final AppUserRepository userRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final CaseNumberService caseNumberService;
    private final CaseMapper caseMapper;

    public CaseService(CaseRepository caseRepository,
                       CitizenRepository citizenRepository,
                       CategoryRepository categoryRepository,
                       DepartmentRepository departmentRepository,
                       AppUserRepository userRepository,
                       StatusHistoryRepository statusHistoryRepository,
                       CaseNumberService caseNumberService,
                       CaseMapper caseMapper) {
        this.caseRepository = caseRepository;
        this.citizenRepository = citizenRepository;
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.caseNumberService = caseNumberService;
        this.caseMapper = caseMapper;
    }

    // =========================================================================
    // CREATE CASE
    // =========================================================================

    /**
     * Creates a new case.
     *
     * Business rules enforced here:
     *  - status is always NEW — never read from the request body.
     *  - createdByUser is always the authenticated user — never from request.
     *  - citizenId, categoryId, departmentId are validated to exist.
     *  - assignedToUserId is optional; validated if provided.
     *  - An initial StatusHistory record (action = CREATE) is written
     *    atomically in the same transaction.
     *
     * @param request   the validated request body
     * @param creatorId the UUID of the authenticated user (from JWT principal)
     */
    @Transactional
    public CaseResponse createCase(CreateCaseRequest request, UUID creatorId) {

        // Resolve all foreign keys — fail fast with meaningful 404s
        AppUser creator = userRepository.findById(creatorId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", creatorId));

        Citizen citizen = citizenRepository.findById(request.getCitizenId())
                .orElseThrow(() -> ResourceNotFoundException.of("Citizen", request.getCitizenId()));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> ResourceNotFoundException.of("Category", request.getCategoryId()));

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Department", request.getDepartmentId()));

        AppUser assignedTo = null;
        if (request.getAssignedToUserId() != null) {
            assignedTo = userRepository.findById(request.getAssignedToUserId())
                    .orElseThrow(() -> ResourceNotFoundException.of("AppUser (assignedTo)",
                            request.getAssignedToUserId()));
        }

        // Generate human-readable case number (atomic, separate transaction)
        String caseNumber = caseNumberService.generateNext();

        // Build the Case entity — status is hardcoded to NEW
        Case newCase = new Case();
        newCase.setCaseNumber(caseNumber);
        newCase.setSubject(request.getSubject());
        newCase.setDescription(request.getDescription());
        newCase.setType(request.getType());
        newCase.setPriority(request.getPriority());
        newCase.setStatus(CaseStatus.NEW);          // <-- ALWAYS NEW, never from request
        newCase.setChannel(request.getChannel());
        newCase.setDueAt(request.getDueAt());
        newCase.setCitizen(citizen);
        newCase.setCategory(category);
        newCase.setDepartment(department);
        newCase.setCreatedByUser(creator);          // <-- ALWAYS authenticated user
        newCase.setAssignedToUser(assignedTo);

        Case saved = caseRepository.save(newCase);

        // Write the initial status history entry (from_status = NULL = creation event)
        StatusHistory history = new StatusHistory();
        history.setCaseEntity(saved);
        history.setFromStatus(null);                // null = no prior status, this is creation
        history.setToStatus(CaseStatus.NEW);
        history.setAction(WorkflowAction.CREATE);
        history.setChangedByUser(creator);
        statusHistoryRepository.save(history);

        return caseMapper.toResponse(saved);
    }

    // =========================================================================
    // SEARCH / LIST CASES
    // =========================================================================

    /**
     * Returns a paginated, filtered list of cases.
     *
     * VISIBILITY (Phase 1):
     *   The createdByUserId parameter is always the authenticated user's UUID.
     *   CaseSpecification enforces the WHERE createdByUser = :userId predicate.
     *   This means an AGENT sees only cases they created — no exceptions.
     *
     * When expanding to SUPERVISOR/ADMIN visibility:
     *   Pass null as createdByUserId here. CaseSpecification skips the predicate
     *   when createdByUserId is null. No other changes needed.
     *
     * Sorting is always createdAt DESC (not client-configurable in Phase 1).
     *
     * @param filter        search filters from query params
     * @param createdByUserId  UUID of authenticated user (Phase 1 visibility scope)
     */
    @Transactional(readOnly = true)
    public PagedResponse<CaseResponse> searchCases(CaseSearchRequest filter,
                                                   UUID createdByUserId) {
        // Sorting fixed to createdAt DESC per requirements
        Pageable pageable = PageRequest.of(
                filter.getPage(),
                filter.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        CaseSpecification spec = new CaseSpecification(filter, createdByUserId);
        Page<Case> page = caseRepository.findAll(spec, pageable);

        List<CaseResponse> content = page.getContent()
                .stream()
                .map(caseMapper::toResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
