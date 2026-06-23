package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.response.DepartmentResponse;
import com.ntg.CitizenLink.entities.Department;
import com.ntg.CitizenLink.repositories.DepartmentRepository;
import com.ntg.CitizenLink.service.interfaces.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}