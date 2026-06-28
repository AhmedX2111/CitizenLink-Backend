package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.GEH.IllegalTransitionException;
import com.ntg.CitizenLink.GEH.ResourceNotFoundException;
import com.ntg.CitizenLink.dto.agent.request.CaseSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CaseTransitionRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCaseRequest;
import com.ntg.CitizenLink.dto.agent.response.CaseActionResponse;
import com.ntg.CitizenLink.dto.agent.response.CaseResponse;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.dto.agent.response.StatusHistoryResponse;
import com.ntg.CitizenLink.entities.*;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.UserRole;
import com.ntg.CitizenLink.enums.WorkflowAction;
import com.ntg.CitizenLink.repositories.*;
import com.ntg.CitizenLink.security.CaseAccessPolicy;
import com.ntg.CitizenLink.service.CaseTransitionRule;
import com.ntg.CitizenLink.service.CaseWorkflowService;
import com.ntg.CitizenLink.service.interfaces.CaseNumberService;
import com.ntg.CitizenLink.service.interfaces.CaseService;
import com.ntg.CitizenLink.service.mapper.CaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseServiceImpl implements CaseService {

    private final CaseRepository caseRepository;
    private final CitizenRepository citizenRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final AppUserRepository userRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final CaseNumberService caseNumberService;
    private final CaseMapper caseMapper;
    private final CaseAccessPolicy caseAccessPolicy;
    private final CaseWorkflowService caseWorkflowService;

    @Override
    @Transactional
    public CaseResponse createCase(CreateCaseRequest request, UUID creatorId) {
        log.info("Creating new case for citizen: {}", request.getCitizenNationalId());

        AppUser creator = userRepository.findById(creatorId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", creatorId));

        Citizen citizen = citizenRepository.findByNationalId(request.getCitizenNationalId())
                .orElseThrow(() -> ResourceNotFoundException.of("Citizen with National ID", request.getCitizenNationalId()));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> ResourceNotFoundException.of("Category", request.getCategoryId()));

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Department", request.getDepartmentId()));

        AppUser assignedTo = null;
        if (request.getAssignedToUserId() != null) {
            assignedTo = userRepository.findById(request.getAssignedToUserId())
                    .orElseThrow(() -> ResourceNotFoundException.of("AppUser (assignedTo)", request.getAssignedToUserId()));
        }

        String caseNumber = caseNumberService.generateNext();

        Case newCase = new Case();
        newCase.setCaseNumber(caseNumber);
        newCase.setSubject(request.getSubject());
        newCase.setDescription(request.getDescription());
        newCase.setType(request.getType());
        newCase.setPriority(request.getPriority());
        newCase.setStatus(CaseStatus.NEW);
        newCase.setChannel(request.getChannel());
        newCase.setDueAt(request.getDueAt());
        newCase.setCitizen(citizen);
        newCase.setCategory(category);
        newCase.setDepartment(department);
        newCase.setCreatedByUser(creator);
        newCase.setAssignedToUser(assignedTo);

        Case saved = caseRepository.save(newCase);

        StatusHistory history = new StatusHistory();
        history.setCaseEntity(saved);
        history.setFromStatus(null);
        history.setToStatus(CaseStatus.NEW);
        history.setAction(WorkflowAction.CREATE);
        history.setChangedByUser(creator);
        statusHistoryRepository.save(history);

        log.info("Case created successfully with ID: {}", saved.getId());
        return caseMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CaseResponse> searchCases(CaseSearchRequest filter, UUID createdByUserId) {
        log.debug("Searching cases for user: {}", createdByUserId);

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

    @Override
    @Transactional(readOnly = true)
    public CaseResponse getCaseById(UUID caseId, UUID requesterId) {
        log.debug("Fetching case {} for requester {}", caseId, requesterId);

        Case found = caseRepository.findById(caseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Case", caseId));

        AppUser requester = userRepository.findById(requesterId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", requesterId));

        if (!caseAccessPolicy.canView(found, requester)) {
            log.warn("User {} attempted to access case {} without permission", requesterId, caseId);
            throw ResourceNotFoundException.of("Case", caseId);
        }

        return caseMapper.toResponse(found);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getCaseTimeline(UUID caseId, UUID requesterId) {
        log.debug("Fetching timeline for case {} requested by {}", caseId, requesterId);

        Case found = caseRepository.findById(caseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Case", caseId));

        AppUser requester = userRepository.findById(requesterId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", requesterId));

        if (!caseAccessPolicy.canView(found, requester)) {
            log.warn("User {} attempted to access timeline for case {} without permission", requesterId, caseId);
            throw ResourceNotFoundException.of("Case", caseId);
        }

        List<StatusHistory> history = statusHistoryRepository
                .findByCaseIdOrderByCreatedAtAsc(caseId);
        return history.stream()
                .map(this::toStatusHistoryResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseActionResponse> getCaseActions(UUID caseId, UUID requesterId) {
        log.debug("Fetching allowed actions for case {} requested by {}", caseId, requesterId);

        Case found = caseRepository.findById(caseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Case", caseId));

        AppUser requester = userRepository.findById(requesterId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", requesterId));

        // Must be able to VIEW before we even consider what actions to offer.
        if (!caseAccessPolicy.canView(found, requester)) {
            log.warn("User {} attempted to access actions for case {} without permission", requesterId, caseId);
            throw ResourceNotFoundException.of("Case", caseId);
        }

        // HANDLER ownership: even though canView() already confirmed HANDLER
        // is the assignee for this case, AGENT never has any actions at all
        // regardless of ownership — that's enforced by getAllowedActions()
        // since no rule in the table lists AGENT in allowedRoles.
        return caseWorkflowService.getAllowedActions(found.getStatus(), requester.getRole());
    }

    @Override
    @Transactional
    public CaseResponse transitionCase(UUID caseId, UUID requesterId, CaseTransitionRequest request) {
        log.info("Transition requested: case={}, action={}, requester={}",
                caseId, request.getAction(), requesterId);

        Case found = caseRepository.findById(caseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Case", caseId));

        AppUser requester = userRepository.findById(requesterId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", requesterId));

        // 1. Must be able to view the case at all (HANDLER must be assignee).
        if (!caseAccessPolicy.canView(found, requester)) {
            log.warn("User {} attempted transition on case {} without view permission",
                    requesterId, caseId);
            throw ResourceNotFoundException.of("Case", caseId);
        }

        // 2. Validate the transition itself: current status + action + role.
        //    Throws IllegalTransitionException (409) per WFL-01 if invalid.
        CaseTransitionRule rule = caseWorkflowService.resolveTransition(
                found.getStatus(), request.getAction(), requester.getRole());

        // 3. WFL-03 / WFL-04: conditionally required fields.
        if (rule.requiresComment() && (request.getComment() == null || request.getComment().isBlank())) {
            throw new IllegalTransitionException(
                    "A comment/reason is required for action " + request.getAction());
        }
        if (rule.requiresResolutionSummary()
                && (request.getResolutionSummary() == null || request.getResolutionSummary().isBlank())) {
            throw new IllegalTransitionException(
                    "A resolution summary is required for action " + request.getAction());
        }

        CaseStatus fromStatus = found.getStatus();
        CaseStatus toStatus = rule.toStatus();

        found.setStatus(toStatus);

        if (rule.requiresResolutionSummary()) {
            found.setResolutionSummary(request.getResolutionSummary());
        }
        if (toStatus == CaseStatus.RESOLVED) {
            found.setResolvedAt(OffsetDateTime.now());
        }
        if (toStatus == CaseStatus.CLOSED) {
            found.setClosedAt(OffsetDateTime.now());
        }

        Case saved = caseRepository.save(found);

        // WFL-02: every transition creates a timeline entry.
        StatusHistory history = new StatusHistory();
        history.setCaseEntity(saved);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setAction(request.getAction());
        history.setChangedByUser(requester);
        history.setComment(rule.requiresComment() ? request.getComment() : null);
        statusHistoryRepository.save(history);

        log.info("Case {} transitioned {} -> {} via {}", caseId, fromStatus, toStatus, request.getAction());

        return caseMapper.toResponse(saved);
    }

    private StatusHistoryResponse toStatusHistoryResponse(StatusHistory sh) {
        return StatusHistoryResponse.builder()
                .id(sh.getId())
                .fromStatus(sh.getFromStatus())
                .toStatus(sh.getToStatus())
                .action(sh.getAction())
                .comment(sh.getComment())
                .createdAt(sh.getCreatedAt())
                .changedByUserId(sh.getChangedByUser().getId())
                .changedByDisplayName(sh.getChangedByUser().getDisplayName())
                .build();
    }
}