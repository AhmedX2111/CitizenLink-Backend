package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.entities.Department;
import com.ntg.CitizenLink.repositories.DepartmentRepository;
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

    private final DepartmentRepository departmentRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<List<Department>> getAllDepartments() {
        log.info("Fetching all active departments");
        List<Department> departments = departmentRepository.findByActiveTrue();
        return ResponseEntity.ok(departments);
    }
}