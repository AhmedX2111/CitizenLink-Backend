package com.ntg.citizenlink.repositories;


import com.ntg.citizenlink.dto.agent.request.CaseSearchRequest;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Case;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Specification for dynamic Case filtering.
 *
 * Visibility rules:
 *   ADMIN / SUPERVISOR — see all cases (createdByUserId = null, assignedToUserId = null)
 *   HANDLER            — see only cases assigned to them (assignedToUserId set)
 *   AGENT              — see only cases they created (createdByUserId set)
 */
public class CaseSpecification implements Specification<Case> {

    private static final char LIKE_ESCAPE_CHAR = '\\';

    private final CaseSearchRequest filter;

    /**
     * When set, filters to cases created by this user (AGENT role).
     * Null = no createdBy restriction.
     */
    private final UUID createdByUserId;

    /**
     * When set, filters to cases assigned to this user (HANDLER role).
     * Null = no assignedTo restriction.
     */
    private final UUID assignedToUserId;

    public CaseSpecification(CaseSearchRequest filter, UUID createdByUserId, UUID assignedToUserId) {
        this.filter = filter;
        this.createdByUserId = createdByUserId;
        this.assignedToUserId = assignedToUserId;
    }

    /**
     * Escapes LIKE wildcards (% and _) and the escape character itself so the
     * keyword is matched literally. Without this, a keyword containing % or _
     * acts as a wildcard and produces unexpected matches and needlessly broad
     * scans. No injection risk either way (the value stays a bound parameter).
     */
    private static String escapeLikeWildcards(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @Override
    public Predicate toPredicate(Root<Case> root,
                                 CriteriaQuery<?> query,
                                 CriteriaBuilder cb) {

        List<Predicate> predicates = new ArrayList<>();

        // ------------------------------------------------------------------
        // VISIBILITY
        //   ADMIN / SUPERVISOR — both null → no restriction
        //   AGENT              — createdByUserId set → filter by creator
        //   HANDLER            — assignedToUserId set → filter by assignee
        // ------------------------------------------------------------------
        if (createdByUserId != null) {
            Join<Case, AppUser> createdBy = root.join("createdByUser", JoinType.INNER);
            predicates.add(cb.equal(createdBy.get("id"), createdByUserId));
        }

        if (assignedToUserId != null) {
            Join<Case, AppUser> assignedToVis = root.join("assignedToUser", JoinType.INNER);
            predicates.add(cb.equal(assignedToVis.get("id"), assignedToUserId));
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
            String pattern = "%" + escapeLikeWildcards(filter.getKeyword().trim().toLowerCase()) + "%";
            Predicate byCaseNumber = cb.like(
                    cb.lower(root.get("caseNumber")), pattern, LIKE_ESCAPE_CHAR);
            Predicate bySubject = cb.like(
                    cb.lower(root.get("subject")), pattern, LIKE_ESCAPE_CHAR);
            predicates.add(cb.or(byCaseNumber, bySubject));
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }
}
