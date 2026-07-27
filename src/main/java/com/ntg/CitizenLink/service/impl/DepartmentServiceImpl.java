package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.exception.ResourceNotFoundException;
import com.ntg.CitizenLink.dto.agent.request.CreateDepartmentRequest;
import com.ntg.CitizenLink.dto.agent.request.UpdateDepartmentRequest;
import com.ntg.CitizenLink.dto.agent.response.DepartmentResponse;
import com.ntg.CitizenLink.entities.Department;
import com.ntg.CitizenLink.repositories.DepartmentRepository;
import com.ntg.CitizenLink.service.interfaces.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllActiveDepartments() {
        log.debug("Fetching all active departments");

        List<Department> departments = departmentRepository.findByActiveTrue();

        return departments.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(UUID id) {
        log.debug("Fetching department by ID: {}", id);

        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Department", id));

        return toResponse(department);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return departmentRepository.existsById(id);
    }

    @Override
    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        String code = generateCode(request.getNameEn());
        log.info("Creating department: nameEn={}, code={}", request.getNameEn(), code);

        Department department = new Department();
        department.setCode(code);
        department.setNameEn(request.getNameEn());
        department.setNameAr(request.getNameAr());
        department.setActive(request.getActive() != null ? request.getActive() : true);

        Department saved = departmentRepository.save(department);
        log.info("Department created: id={}, code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartment(UUID id, UpdateDepartmentRequest request) {
        log.info("Updating department: id={}", id);

        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Department", id));

        department.setNameEn(request.getNameEn());
        department.setNameAr(request.getNameAr());
        department.setActive(request.getActive());

        Department saved = departmentRepository.save(department);
        log.info("Department updated: id={}, code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    private String generateCode(String nameEn) {
        String base = nameEn.toUpperCase()
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Z0-9_]", "")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (base.isEmpty()) base = "DEPARTMENT";
        if (base.length() > 50) base = base.substring(0, 50);

        String candidate = base;
        int suffix = 2;
        while (departmentRepository.findByCode(candidate).isPresent()) {
            String suffixStr = "_" + suffix;
            int maxLen = 50 - suffixStr.length();
            candidate = (base.length() > maxLen ? base.substring(0, maxLen) : base) + suffixStr;
            suffix++;
        }
        return candidate;
    }

    /**
     * Convert Department entity to DepartmentResponse DTO
     */
    private DepartmentResponse toResponse(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .code(department.getCode())
                .nameEn(department.getNameEn())
                .nameAr(department.getNameAr())
                .active(department.getActive())
                .build();
    }
}
