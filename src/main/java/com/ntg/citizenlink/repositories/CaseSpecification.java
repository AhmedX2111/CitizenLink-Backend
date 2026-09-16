package com.ntg.citizenlink.repositories;


import com.ntg.citizenlink.dto.agent.request.CaseSearchRequest;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.enums.CaseStatus;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    /**
     * US-54: final (terminal) workflow states excluded from the workload
     * quick filters (overdue / dueToday / unassigned) when no explicit
     * status filter is present — overdue closed/cancelled cases are no
     * longer actionable workload. Mirrors InboxSpecification.FINAL_STATES.
     */
    private static final Set<CaseStatus> FINAL_STATES = Set.of(CaseStatus.CLOSED, CaseStatus.CANCELLED);

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

    /**
     * US-54: reference instant for the overdue predicate (dueAt &lt; now).
     * Computed by the service layer from the app time zone so counts and
     * the linked case list agree; null falls back to the current instant
     * (only reachable via the 3-arg constructor).
     */
    private final OffsetDateTime now;

    /** US-54: inclusive start of "today" for the dueToday window. */
    private final OffsetDateTime todayStart;

    /** US-54: exclusive end of "today" for the dueToday window. */
    private final OffsetDateTime todayEnd;

    public CaseSpecification(CaseSearchRequest filter, UUID createdByUserId, UUID assignedToUserId) {
        this(filter, createdByUserId, assignedToUserId, null, null, null);
    }

    /**
     * US-54: full constructor — the service passes the now/today window it
     * resolved via AppTimeZone so the dashboard counts and the case list the
     * indicators link to evaluate the predicates against identical instants.
     */
    public CaseSpecification(CaseSearchRequest filter, UUID createdByUserId, UUID assignedToUserId,
                             OffsetDateTime now, OffsetDateTime todayStart, OffsetDateTime todayEnd) {
        this.filter = filter;
        this.createdByUserId = createdByUserId;
        this.assignedToUserId = assignedToUserId;
        this.now = now;
        this.todayStart = todayStart;
        this.todayEnd = todayEnd;
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

        // ------------------------------------------------------------------
        // US-54 workload quick filters (independent, ANDed). When no explicit
        // status filter is present, final states are excluded so the lists
        // show actionable workload — matching what the dashboard indicators
        // count. An explicit status filter overrides the exclusion.
        // ------------------------------------------------------------------
        boolean statusExplicit = filter.getStatus() != null;
        if (!statusExplicit && workloadFilterPresent()) {
            predicates.add(cb.not(root.get("status").in(FINAL_STATES)));
        }

        if (Boolean.TRUE.equals(filter.getOverdue())) {
            Path<OffsetDateTime> dueAt = root.<OffsetDateTime>get("dueAt");
            predicates.add(cb.and(cb.isNotNull(dueAt), cb.lessThan(dueAt, effectiveNow())));
        }

        if (Boolean.TRUE.equals(filter.getDueToday())) {
            Path<OffsetDateTime> dueAt = root.<OffsetDateTime>get("dueAt");
            predicates.add(cb.and(
                    cb.greaterThanOrEqualTo(dueAt, effectiveTodayStart()),
                    cb.lessThan(dueAt, effectiveTodayEnd())));
        }

        if (Boolean.TRUE.equals(filter.getUnassigned())) {
            predicates.add(cb.isNull(root.get("assignedToUser")));
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }

    private boolean workloadFilterPresent() {
        return Boolean.TRUE.equals(filter.getOverdue())
                || Boolean.TRUE.equals(filter.getDueToday())
                || Boolean.TRUE.equals(filter.getUnassigned());
    }

    private OffsetDateTime effectiveNow() {
        return now != null ? now : OffsetDateTime.now();
    }

    private OffsetDateTime effectiveTodayStart() {
        if (todayStart != null) {
            return todayStart;
        }
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
    }

    private OffsetDateTime effectiveTodayEnd() {
        if (todayEnd != null) {
            return todayEnd;
        }
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toOffsetDateTime();
    }
}
