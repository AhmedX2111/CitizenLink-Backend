package com.ntg.CitizenLink.repositories;


import com.ntg.CitizenLink.dto.agent.request.CaseSearchRequest;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.entities.Case;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Specification for dynamic Case filtering.
 *
 * VISIBILITY RULE (Phase 1):
 *   createdByUserId is always injected by CaseService from the authenticated
 *   principal — never from the request body. This enforces that an AGENT
 *   can only see cases they created.
 *
 * When the visibility model expands (e.g. SUPERVISOR sees all), remove or
 * conditionally skip the createdByUserId predicate in CaseService.
 * No code changes required here.
 */
public class CaseSpecification implements Specification<Case> {

    private final CaseSearchRequest filter;

    /**
     * The UUID of the authenticated user.
     * Always set to enforce Phase 1 visibility.
     * Set to null to disable the restriction (SUPERVISOR / ADMIN).
     */
    private final UUID createdByUserId;

    public CaseSpecification(CaseSearchRequest filter, UUID createdByUserId) {
        this.filter = filter;
        this.createdByUserId = createdByUserId;
    }

    @Override
    public Predicate toPredicate(Root<Case> root,
                                 CriteriaQuery<?> query,
                                 CriteriaBuilder cb) {

        List<Predicate> predicates = new ArrayList<>();

        // ------------------------------------------------------------------
        // VISIBILITY: restrict to cases created by the authenticated user.
        // Phase 1 — AGENT sees only their own cases.
        // ------------------------------------------------------------------
        if (createdByUserId != null) {
            Join<Case, AppUser> createdBy = root.join("createdByUser", JoinType.INNER);
            predicates.add(cb.equal(createdBy.get("id"), createdByUserId));
        }

        // ------------------------------------------------------------------
        // FILTERS (all optional, combined with AND)
        // ------------------------------------------------------------------
        if (filter.getStatus() != null) {
            predicates.add(cb.equal(root.get("status"), filter.getStatus()));
        }

        if (filter.getType() != null) {
            predicates.add(cb.equal(root.get("type"), filter.getType()));
        }

        if (filter.getPriority() != null) {
            predicates.add(cb.equal(root.get("priority"), filter.getPriority()));
        }

        if (filter.getAssignedToUserId() != null) {
            Join<Case, AppUser> assignedTo = root.join("assignedToUser", JoinType.LEFT);
            predicates.add(cb.equal(assignedTo.get("id"), filter.getAssignedToUserId()));
        }

        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
            String pattern = "%" + filter.getKeyword().trim().toLowerCase() + "%";
            Predicate byCaseNumber = cb.like(
                    cb.lower(root.get("caseNumber")), pattern);
            Predicate bySubject = cb.like(
                    cb.lower(root.get("subject")), pattern);
            predicates.add(cb.or(byCaseNumber, bySubject));
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }
}
