package com.ntg.citizenlink.repositories;


import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.dto.agent.response.MyOpenCaseResponse;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.CaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Case}.
 *
 * Extends JpaSpecificationExecutor to support dynamic filtering
 * via CaseSpecification without writing raw JPQL for every filter combo.
 * All filtered/paginated queries go through findAll(Specification, Pageable).
 */
@Repository
public interface CaseRepository extends JpaRepository<Case, UUID>,
        JpaSpecificationExecutor<Case> {

    /**
     * Checks for duplicate case_number before insert.
     */
    boolean existsByCaseNumber(String caseNumber);

    /**
     * All filtered/paginated search goes through this override so the five
     * associations CaseMapper.toResponse reads (citizen, category, department,
     * createdByUser, assignedToUser) are fetched in the same query.
     *
     * M-18: without the entity graph each mapped row triggered up to 5 lazy
     * selects (up to 100 extra queries for a default 20-row page). All five
     * associations are to-one, so the fetch uses SQL LEFT JOINs and Hibernate
     * still applies LIMIT/OFFSET in SQL — no duplicate rows, no in-memory
     * pagination, and the generated count query is unaffected.
     */
    @EntityGraph(attributePaths = {"citizen", "category", "department",
            "createdByUser", "assignedToUser"})
    @Override
    Page<Case> findAll(Specification<Case> spec, Pageable pageable);

    // Count total cases for a citizen
    long countByCitizenId(UUID citizenId);

    // Count cases with status not in specified list - Use CaseStatus enum
    @Query("SELECT COUNT(c) FROM Case c WHERE c.citizen.id = :citizenId AND c.status NOT IN :statuses")
    long countByCitizenIdAndStatusNotIn(@Param("citizenId") UUID citizenId, @Param("statuses") List<CaseStatus> statuses);

    // Count cases with status in specified list - Use CaseStatus enum
    @Query("SELECT COUNT(c) FROM Case c WHERE c.citizen.id = :citizenId AND c.status IN :statuses")
    long countByCitizenIdAndStatusIn(@Param("citizenId") UUID citizenId, @Param("statuses") List<CaseStatus> statuses);

    // ── Citizen profile (M-17): visibility-aware recent cases + totals ─────
    /**
     * Top recent cases for a citizen, restricted to what the requester may
     * see. Visibility is encoded the same way as CaseSpecification:
     *   - both filters null  -> ADMIN/SUPERVISOR sees all of the citizen's cases
     *   - createdByUserId set -> only cases the AGENT created
     *   - assignedToUserId set -> only cases assigned to the HANDLER
     * Uses LEFT JOIN FETCH so the caller never triggers a lazy assignedToUser
     * load per case (all associations are to-one, so Hibernate still applies
     * the LIMIT in SQL).
     */
    @Query("""
        SELECT c
        FROM Case c
        LEFT JOIN FETCH c.assignedToUser
        WHERE c.citizen.id = :citizenId
          AND (:createdByUserId IS NULL OR c.createdByUser.id = :createdByUserId)
          AND (:assignedToUserId IS NULL OR c.assignedToUser.id = :assignedToUserId)
        ORDER BY c.createdAt DESC, c.id DESC
        """)
    List<Case> findVisibleByCitizenIdOrderByCreatedAtDesc(
            @Param("citizenId") UUID citizenId,
            @Param("createdByUserId") UUID createdByUserId,
            @Param("assignedToUserId") UUID assignedToUserId,
            Pageable pageable
    );

    /**
     * Counts a citizen's cases grouped by status, applying the same
     * requester-visibility restriction as findVisibleByCitizenIdOrderByCreatedAtDesc.
     * Returns Object[]{CaseStatus, Long} pairs for statuses with at least one
     * visible case; statuses with zero visible cases are not included.
     */
    @Query("""
        SELECT c.status, COUNT(c)
        FROM Case c
        WHERE c.citizen.id = :citizenId
          AND (:createdByUserId IS NULL OR c.createdByUser.id = :createdByUserId)
          AND (:assignedToUserId IS NULL OR c.assignedToUser.id = :assignedToUserId)
        GROUP BY c.status
        """)
    List<Object[]> countVisibleByCitizenIdByStatus(
            @Param("citizenId") UUID citizenId,
            @Param("createdByUserId") UUID createdByUserId,
            @Param("assignedToUserId") UUID assignedToUserId
    );

    /**
     * Counts a citizen's cases with the same requester-visibility restriction
     * as countVisibleByCitizenIdByStatus (single scalar count instead of a
     * grouped result). L-05: used by getCitizenById so the basic-profile case
     * count matches the access-filtered totals shown in the 360 profile.
     */
    @Query("""
        SELECT COUNT(c)
        FROM Case c
        WHERE c.citizen.id = :citizenId
          AND (:createdByUserId IS NULL OR c.createdByUser.id = :createdByUserId)
          AND (:assignedToUserId IS NULL OR c.assignedToUser.id = :assignedToUserId)
        """)
    long countVisibleByCitizenId(
            @Param("citizenId") UUID citizenId,
            @Param("createdByUserId") UUID createdByUserId,
            @Param("assignedToUserId") UUID assignedToUserId
    );

    // ── US-04: KPI counts ──────────────────────────────────────────────
    /**
     * Open cases = NEW, ASSIGNED, IN_PROGRESS (per confirmed business rule).
     * Excludes AWAITING_INFO, SUSPENDED, RESOLVED, CLOSED, CANCELLED.
     */
    @Query("""
        SELECT COUNT(c) FROM Case c
        WHERE c.status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS')
        """)
    long countOpenCases();

    /**
     * Cases resolved within the current calendar month.
     * Uses resolvedAt (set by CaseService on RESOLVED transition), not createdAt.
     */
    @Query("""
        SELECT COUNT(c) FROM Case c
        WHERE c.resolvedAt IS NOT NULL
        AND c.resolvedAt >= :monthStart
        AND c.resolvedAt < :monthEnd
        """)
    long countResolvedBetween(@Param("monthStart") OffsetDateTime monthStart,
                              @Param("monthEnd") OffsetDateTime monthEnd);

    /**
     * Overdue = dueAt has passed AND case is not in a terminal state.
     * Org-wide KPI — not scoped to any user (confirmed business rule).
     */
    @Query("""
        SELECT COUNT(c) FROM Case c
        WHERE c.dueAt IS NOT NULL
        AND c.dueAt < :now
        AND c.status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED')
        """)
    long countOverdueCases(@Param("now") OffsetDateTime now);

    /**
     * Cases created today (calendar day boundaries passed from service layer
     * to keep timezone handling explicit and testable).
     */
    @Query("""
        SELECT COUNT(c) FROM Case c
        WHERE c.createdAt >= :dayStart
        AND c.createdAt < :dayEnd
        """)
    long countCreatedBetween(@Param("dayStart") OffsetDateTime dayStart,
                             @Param("dayEnd") OffsetDateTime dayEnd);


    // ── US-05: Status breakdown for chart ────────────────────────────────
    /**
     * Groups all cases by status. Returns Object[]{CaseStatus, Long} pairs.
     * Statuses with zero cases are NOT included here — the service layer
     * fills in zero-count statuses so the chart always shows all 8 categories.
     */
    @Query("""
        SELECT c.status, COUNT(c) FROM Case c
        GROUP BY c.status
        """)
    List<Object[]> countGroupedByStatus();

    // ── US-06: My Open Cases (HANDLER) ───────────────────────────────────
    /**
     * Top 5 open cases assigned to the given user, ordered by due date
     * ascending (most urgent first), nulls last.
     */
    @Query("""
        SELECT new com.ntg.citizenlink.dto.agent.response.MyOpenCaseResponse(
            c.id, c.caseNumber, c.subject, c.status, c.dueAt
        )
        FROM Case c
        WHERE c.assignedToUser.id = :userId
        AND c.status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS')
        ORDER BY CASE WHEN c.dueAt IS NULL THEN 1 ELSE 0 END, c.dueAt ASC
        """)
    List<MyOpenCaseResponse> findTop5OpenCasesByAssignedUser(@Param("userId") UUID userId,
                                                               org.springframework.data.domain.Pageable pageable);

    // ── US-28: CSV export ───────────────────────────────────────────────────
    /**
     * Fetch cases within a createdAt date range, ordered by createdAt DESC,
     * paged so the exporter never materialises the whole result set.
     * Uses JOIN FETCH to load all relationships eagerly; all associations are
     * to-one, so Hibernate applies the LIMIT/OFFSET in SQL (no in-memory paging).
     * The id tiebreaker keeps pagination stable when createdAt ties.
     */
    @Query("""
        SELECT DISTINCT c FROM Case c
        LEFT JOIN FETCH c.citizen
        LEFT JOIN FETCH c.category
        LEFT JOIN FETCH c.department
        LEFT JOIN FETCH c.createdByUser
        LEFT JOIN FETCH c.assignedToUser
        WHERE c.createdAt >= :startDate AND c.createdAt < :endDate
        ORDER BY c.createdAt DESC, c.id DESC
        """)
    Slice<Case> findCasesForReportBetween(@Param("startDate") OffsetDateTime startDate,
                                          @Param("endDate") OffsetDateTime endDate,
                                          Pageable pageable);
}
