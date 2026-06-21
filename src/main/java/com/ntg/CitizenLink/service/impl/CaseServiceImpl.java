package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.GEH.ResourceNotFoundException;
import com.ntg.CitizenLink.dto.agent.request.CaseSearchRequest;
import com.ntg.CitizenLink.dto.agent.request.CreateCaseRequest;
import com.ntg.CitizenLink.dto.agent.response.CaseResponse;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.entities.*;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.WorkflowAction;
import com.ntg.CitizenLink.repositories.*;
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
}
