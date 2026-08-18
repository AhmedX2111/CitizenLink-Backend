package com.ntg.citizenlink.service.mapper;


import com.ntg.citizenlink.dto.agent.response.CaseResponse;
import com.ntg.citizenlink.entities.Case;
import org.springframework.stereotype.Component;

/**
 * Maps {@link Case} entities to {@link CaseResponse} DTOs.
 *
 * Keeps controllers and services free of mapping logic.
 * All lazy associations accessed here must be within an open Hibernate session
 * (i.e. called from inside a @Transactional method or with JOIN FETCH).
 */
@Component
public class CaseMapper {

    public CaseResponse toResponse(Case c) {
        CaseResponse r = new CaseResponse();

        r.setId(c.getId());
        r.setCaseNumber(c.getCaseNumber());
        r.setSubject(c.getSubject());
        r.setDescription(c.getDescription());
        r.setType(c.getType());
        r.setPriority(c.getPriority());
        r.setStatus(c.getStatus());
        r.setChannel(c.getChannel());
        r.setResolutionSummary(c.getResolutionSummary());
        r.setDueAt(c.getDueAt());
        r.setCreatedAt(c.getCreatedAt());
        r.setUpdatedAt(c.getUpdatedAt());
        r.setResolvedAt(c.getResolvedAt());
        r.setClosedAt(c.getClosedAt());

        // Citizen
        if (c.getCitizen() != null) {
            r.setCitizenId(c.getCitizen().getId());
            r.setCitizenFullName(c.getCitizen().getFullName());
            r.setCitizenNationalId(c.getCitizen().getNationalId());
            r.setCitizenPhone(c.getCitizen().getPhone());
        }

        // Category
        if (c.getCategory() != null) {
            r.setCategoryId(c.getCategory().getId());
            r.setCategoryNameEn(c.getCategory().getNameEn());
            r.setCategoryNameAr(c.getCategory().getNameAr());
        }

        // Department
        if (c.getDepartment() != null) {
            r.setDepartmentId(c.getDepartment().getId());
            r.setDepartmentNameEn(c.getDepartment().getNameEn());
            r.setDepartmentNameAr(c.getDepartment().getNameAr());
        }

        // Created by
        if (c.getCreatedByUser() != null) {
            r.setCreatedByUserId(c.getCreatedByUser().getId());
            r.setCreatedByDisplayName(c.getCreatedByUser().getDisplayName());
        }

        // Assigned to (nullable)
        if (c.getAssignedToUser() != null) {
            r.setAssignedToUserId(c.getAssignedToUser().getId());
            r.setAssignedToDisplayName(c.getAssignedToUser().getDisplayName());
        }

        return r;
    }
}
