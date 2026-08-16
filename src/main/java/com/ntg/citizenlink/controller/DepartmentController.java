package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.dto.agent.request.CreateDepartmentRequest;
import com.ntg.citizenlink.dto.agent.request.UpdateDepartmentRequest;
import com.ntg.citizenlink.dto.agent.response.DepartmentResponse;
import com.ntg.citizenlink.service.interfaces.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<List<DepartmentResponse>> getAllDepartments() {
        log.info("GET /api/v1/departments - fetching all active departments");

        List<DepartmentResponse> departments = departmentService.getAllActiveDepartments();

        log.info("GET /api/v1/departments - found {} departments", departments.size());
        return ResponseEntity.ok(departments);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody CreateDepartmentRequest request) {
        log.info("POST /api/v1/departments - creating department: nameEn={}", request.getNameEn());

        DepartmentResponse response = departmentService.createDepartment(request);

        log.info("POST /api/v1/departments - created department: id={}, code={}", response.getId(), response.getCode());
        return ResponseEntity.created(URI.create("/api/v1/departments/" + response.getId())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        log.info("PUT /api/v1/departments/{} - updating department", id);

        DepartmentResponse response = departmentService.updateDepartment(id, request);

        log.info("PUT /api/v1/departments/{} - updated successfully", id);
        return ResponseEntity.ok(response);
    }
}