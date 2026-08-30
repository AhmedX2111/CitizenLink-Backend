package com.ntg.citizenlink.repositories;

import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.InboxSort;
import com.ntg.citizenlink.enums.Priority;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * US-49: JPA Specification for the HANDLER work inbox. Extended by US-50/US-51.
 *
 * Dynamic filtering is done via Specification (not JPQL with nullable
 * parameters) to avoid the nullable-enum JPQL pitfall — PostgreSQL cannot
 * infer the type of an unbound NULL enum parameter in
 * {@code (:status IS NULL OR c.status = :status)} style predicates, while
 * Criteria predicates are only added when the filter is present.
 *
 * Semantics:
 *   - assignedToUserId   — always applied (the inbox is per-handler)
 *   - status             — when explicitly given, that exact status is
 *                          filtered (documented override). When absent,
 *                          final states (CLOSED, CANCELLED) are EXCLUDED so
 *                          the inbox only holds live work. AWAITING_INFO and
 *                          SUSPENDED are non-final and stay included (unlike
 *                          the US-06 top-5 widget query).
 *   - priority           — optional exact match
 *   - keyword            — matches case_number prefix OR subject LIKE,
 *                          case-insensitive, wildcard-escaped
 *
 * US-50 additions (urgency quick filters):
 *   - overdue            — when TRUE: dueAt IS NOT NULL AND dueAt < now.
 *                          Absent/FALSE applies no overdue filtering.
 *   - dueToday           — when TRUE: todayStart &lt;= dueAt &lt; todayEnd
 *                          (calendar "today" in the app time zone, resolved
 *                          by DashboardServiceImpl via app.time-zone — the
 *                          same mechanism CaseNumberServiceImpl uses for the
 *                          case-number year). Absent/FALSE applies none.
 *                          overdue and dueToday are independent ANDed
 *                          predicates. For due dates strictly before today
 *                          or inside today but not yet past they are
 *                          mutually exclusive — both TRUE then matches
 *                          nothing. Edge case: a case due EARLIER TODAY
 *                          (dueAt in the past but still inside today's
 *                          window) satisfies both formulas and is the only
 *                          thing overdue=TRUE &amp; dueToday=TRUE can return.
 *
 * Composed behaviour with the default exclusion: all predicates are ANDed,
 * so the final-state exclusion keeps working under the new dimensions —
 * e.g. overdue=TRUE without an explicit status never returns a CLOSED case
 * (it fails the NOT-IN-(CLOSED, CANCELLED) default), while
 * status=CLOSED&amp;overdue=TRUE explicitly overrides the default and lists
 * closed cases that are past due.
 *
 * US-51 additions (server-side ranking): the {@code sort} option is applied
 * INSIDE the query as CriteriaBuilder order items — the overdue-first flag
 * and the priority rank are CASE expressions that Spring Data's Sort cannot
 * express, and passing a Pageable Sort would overwrite any order items set
 * here. Ordering is applied only to content queries: Spring Data derives the
 * pagination-total count query by re-running this specification against a
 * Long-typed criteria query, and ORDER BY over an aggregate select is
 * invalid on PostgreSQL.
 */
public class InboxSpecification implements Specification<Case> {

    private static final char LIKE_ESCAPE_CHAR = '\\';

    /**
     * Final (terminal) workflow states excluded from the inbox by default.
     * Deliberately does NOT contain AWAITING_INFO / SUSPENDED — those are
     * paused-but-alive states a handler still has to work on.
     */
    private static final Set<CaseStatus> FINAL_STATES = Set.of(CaseStatus.CLOSED, CaseStatus.CANCELLED);

    private final UUID assignedToUserId;
    private final CaseStatus status;
    private final Priority priority;
    private final String keyword;
    private final Boolean overdue;
    private final Boolean dueToday;
    /** Reference instant for the overdue predicate (dueAt < now). */
    private final OffsetDateTime now;
    /** Inclusive start of "today" in the app time zone (dueToday window). */
    private final OffsetDateTime todayStart;
    /** Exclusive end of "today" in the app time zone (dueToday window). */
    private final OffsetDateTime todayEnd;
    private final InboxSort sort;

    private InboxSpecification(UUID assignedToUserId, CaseStatus status, Priority priority, String keyword,
                               Boolean overdue, Boolean dueToday,
                               OffsetDateTime now, OffsetDateTime todayStart, OffsetDateTime todayEnd,
                               InboxSort sort) {
        this.assignedToUserId = assignedToUserId;
        this.status = status;
        this.priority = priority;
        this.keyword = keyword;
        this.overdue = overdue;
        this.dueToday = dueToday;
        this.now = now;
        this.todayStart = todayStart;
        this.todayEnd = todayEnd;
        this.sort = sort;
    }

    /**
     * US-49/US-50/US-51 entry point — everything is optional except the
     * inbox owner; unset dimensions apply no predicate (see class javadoc).
     */
    public static Builder forHandler(UUID assignedToUserId) {
        return new Builder(assignedToUserId);
    }

    /**
     * Fluent builder for the inbox specification — the counts endpoint and
     * the paged inbox share the exact same base conditions, each enabling
     * only the dimensions it needs.
     */
    public static final class Builder {

        private final UUID assignedToUserId;
        private CaseStatus status;
        private Priority priority;
        private String keyword;
        private Boolean overdue;
        private Boolean dueToday;
        private OffsetDateTime now;
        private OffsetDateTime todayStart;
        private OffsetDateTime todayEnd;
        private InboxSort sort;

        private Builder(UUID assignedToUserId) {
            this.assignedToUserId = assignedToUserId;
        }

        /** Explicit status filter — overrides the default final-state exclusion. */
        public Builder status(CaseStatus status) {
            this.status = status;
            return this;
        }

        public Builder priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        /**
         * US-50: overdue quick filter. TRUE requires dueAt != null AND
         * dueAt &lt; now (passed so the predicate is evaluated against one
         * consistent instant).
         */
        public Builder overdue(Boolean overdue, OffsetDateTime now) {
            this.overdue = overdue;
            this.now = now;
            return this;
        }

        /**
         * US-50: due-today quick filter. TRUE requires todayStart &lt;= dueAt
         * &lt; todayEnd, both computed in the app time zone by the caller.
         */
        public Builder dueToday(Boolean dueToday, OffsetDateTime todayStart, OffsetDateTime todayEnd) {
            this.dueToday = dueToday;
            this.todayStart = todayStart;
            this.todayEnd = todayEnd;
            return this;
        }

        /** US-51: ordering applied inside the query (null = no ordering). */
        public Builder sort(InboxSort sort) {
            this.sort = sort;
            return this;
        }

        public InboxSpecification build() {
            return new InboxSpecification(assignedToUserId, status, priority, keyword,
                    overdue, dueToday, now, todayStart, todayEnd, sort);
        }
    }

    /**
     * Escapes LIKE wildcards (% and _) and the escape character itself so the
     * keyword is matched literally. Mirrors the escapeLikeWildcards approach in
     * CaseSpecification (duplicated here so CaseSpecification stays untouched);
     * without this, a keyword containing % or _ acts as a wildcard.
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
        // AC1 — the inbox only ever contains cases assigned to this handler.
        // INNER join: an inbox row without an assignee makes no sense.
        // ------------------------------------------------------------------
        Join<Case, AppUser> assignedTo = root.join("assignedToUser", JoinType.INNER);
        predicates.add(cb.equal(assignedTo.get("id"), assignedToUserId));

        // ------------------------------------------------------------------
        // AC2 — status: explicit filter overrides; otherwise exclude finals.
        // ------------------------------------------------------------------
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        } else {
            predicates.add(cb.not(root.get("status").in(FINAL_STATES)));
        }

        // ------------------------------------------------------------------
        // Optional exact-match filters (combined with AND)
        // ------------------------------------------------------------------
        if (priority != null) {
            predicates.add(cb.equal(root.get("priority"), priority));
        }

        // ------------------------------------------------------------------
        // Optional keyword — case_number prefix OR subject LIKE,
        // case-insensitive, wildcards escaped (mirrors CaseSpecification).
        // ------------------------------------------------------------------
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + escapeLikeWildcards(keyword.trim().toLowerCase()) + "%";
            Predicate byCaseNumber = cb.like(
                    cb.lower(root.get("caseNumber")), pattern, LIKE_ESCAPE_CHAR);
            Predicate bySubject = cb.like(
                    cb.lower(root.get("subject")), pattern, LIKE_ESCAPE_CHAR);
            predicates.add(cb.or(byCaseNumber, bySubject));
        }

        // ------------------------------------------------------------------
        // US-50 — urgency quick filters (independent, ANDed).
        // ------------------------------------------------------------------
        if (Boolean.TRUE.equals(overdue)) {
            predicates.add(isOverdue(root, cb));
        }

        if (Boolean.TRUE.equals(dueToday)) {
            predicates.add(isDueToday(root, cb));
        }

        // ------------------------------------------------------------------
        // US-51 — ordering inside the query (content queries only).
        // ------------------------------------------------------------------
        applyOrdering(root, query, cb);

        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * US-50: overdue = dueAt is set and lies in the past.
     */
    private Predicate isOverdue(Root<Case> root, CriteriaBuilder cb) {
        Path<OffsetDateTime> dueAt = root.<OffsetDateTime>get("dueAt");
        return cb.and(cb.isNotNull(dueAt), cb.lessThan(dueAt, now));
    }

    /**
     * US-50: due today = dueAt inside the current calendar day in the app
     * time zone (half-open window [todayStart, todayEnd)).
     */
    private Predicate isDueToday(Root<Case> root, CriteriaBuilder cb) {
        Path<OffsetDateTime> dueAt = root.<OffsetDateTime>get("dueAt");
        return cb.and(
                cb.greaterThanOrEqualTo(dueAt, todayStart),
                cb.lessThan(dueAt, todayEnd));
    }

    /**
     * US-51: applies the selected ordering as CriteriaBuilder order items.
     *
     * Skipped for the Long-typed count query Spring Data derives from this
     * specification for pagination totals — ORDER BY over an aggregate
     * select is invalid on PostgreSQL. Also skipped when no sort is set
     * (the counts path), where ordering is irrelevant anyway.
     */
    private void applyOrdering(Root<Case> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (sort == null || Long.class.equals(query.getResultType())) {
            return;
        }

        List<Order> orders = new ArrayList<>();
        switch (sort) {
            // SMART: overdue first, then priority rank, then nearest due date.
            case SMART -> {
                orders.add(cb.asc(overdueFlag(root, cb)));
                orders.add(cb.asc(priorityRank(root, cb)));
                orders.add(cb.asc(root.<OffsetDateTime>get("dueAt")));
            }
            // DUE_DATE: the US-49 default — nearest due date.
            case DUE_DATE -> orders.add(cb.asc(root.<OffsetDateTime>get("dueAt")));
            // PRIORITY: rank, then nearest due date for equal priorities.
            case PRIORITY -> {
                orders.add(cb.asc(priorityRank(root, cb)));
                orders.add(cb.asc(root.<OffsetDateTime>get("dueAt")));
            }
            // NEWEST: most recently updated first.
            case NEWEST -> orders.add(cb.desc(root.<OffsetDateTime>get("updatedAt")));
        }
        if (sort != InboxSort.NEWEST) {
            // US-51: dueAt nulls LAST for every due-date-aware option —
            // expressed as an explicit 0/1 flag (1 = NULL) instead of
            // Order.nullsLast(), which the resolved Jakarta Persistence API
            // does not expose. Deterministic regardless of the DB's default
            // null ordering (H2 DEFAULT_NULL_ORDERING vs PostgreSQL).
            orders.add(cb.asc(dueAtNullFlag(root, cb)));
        }
        // Stable-pagination tiebreaker shared by every option.
        orders.add(cb.asc(root.get("id")));

        query.orderBy(orders);
    }

    /**
     * US-51: 0 when the case is overdue, 1 otherwise — ascending order puts
     * overdue cases first. A NULL dueAt never satisfies lessThan, so
     * undated cases rank as non-overdue.
     */
    private Expression<Integer> overdueFlag(Root<Case> root, CriteriaBuilder cb) {
        Path<OffsetDateTime> dueAt = root.<OffsetDateTime>get("dueAt");
        return cb.<Integer>selectCase()
                .when(cb.lessThan(dueAt, now), 0)
                .otherwise(1);
    }

    /**
     * US-51: URGENT=0, HIGH=1, MEDIUM=2, LOW=3 — ascending order puts the
     * most urgent work first.
     */
    private Expression<Integer> priorityRank(Root<Case> root, CriteriaBuilder cb) {
        Path<Priority> priorityPath = root.<Priority>get("priority");
        return cb.<Integer>selectCase()
                .when(cb.equal(priorityPath, Priority.URGENT), 0)
                .when(cb.equal(priorityPath, Priority.HIGH), 1)
                .when(cb.equal(priorityPath, Priority.MEDIUM), 2)
                .otherwise(3);
    }

    /**
     * US-51: 0 when dueAt is set, 1 when NULL — ordering by this flag before
     * dueAt yields the nulls-last semantics the US-49 contract guarantees,
     * independent of the database's default null ordering.
     */
    private Expression<Integer> dueAtNullFlag(Root<Case> root, CriteriaBuilder cb) {
        Path<OffsetDateTime> dueAt = root.<OffsetDateTime>get("dueAt");
        return cb.<Integer>selectCase()
                .when(cb.isNotNull(dueAt), 0)
                .otherwise(1);
    }
}
