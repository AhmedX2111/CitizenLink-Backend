package com.ntg.CitizenLink.service.interfaces;

import com.ntg.CitizenLink.dto.agent.request.CreateDepartmentRequest;
import com.ntg.CitizenLink.dto.agent.request.UpdateDepartmentRequest;
import com.ntg.CitizenLink.dto.agent.response.DepartmentResponse;

import java.util.List;
import java.util.UUID;

public interface DepartmentService {

    /**
     * Get all active departments
     */
    List<DepartmentResponse> getAllActiveDepartments();

    /**
     * Get department by ID
     */
    DepartmentResponse getDepartmentById(UUID id);

    /**
     * Check if department exists
     */
    boolean existsById(UUID id);

    /**
     * Create a new department
     */
    DepartmentResponse createDepartment(CreateDepartmentRequest request);

    /**
     * Update an existing department
     */
    DepartmentResponse updateDepartment(UUID id, UpdateDepartmentRequest request);
}
