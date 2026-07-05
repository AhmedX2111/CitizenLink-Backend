package com.ntg.CitizenLink.repositories;


import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.dto.agent.response.MyOpenCaseResponse;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.CaseStatus;
import org.springframework.data.domain.Pageable;
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

    // Count total cases for a citizen
    long countByCitizenId(UUID citizenId);

    // Count cases with status not in specified list - Use CaseStatus enum
    @Query("SELECT COUNT(c) FROM Case c WHERE c.citizen.id = :citizenId AND c.status NOT IN :statuses")
    long countByCitizenIdAndStatusNotIn(@Param("citizenId") UUID citizenId, @Param("statuses") List<CaseStatus> statuses);

    // Count cases with status in specified list - Use CaseStatus enum
    @Query("SELECT COUNT(c) FROM Case c WHERE c.citizen.id = :citizenId AND c.status IN :statuses")
    long countByCitizenIdAndStatusIn(@Param("citizenId") UUID citizenId, @Param("statuses") List<CaseStatus> statuses);

    // Get recent cases for a citizen
    @Query("""
    SELECT c
    FROM Case c
    WHERE c.citizen.id = :citizenId
    ORDER BY c.createdAt DESC
""")
    List<Case> findByCitizenIdOrderByCreatedAtDesc(
            @Param("citizenId") UUID citizenId,
            Pageable pageable
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
        SELECT new com.ntg.CitizenLink.dto.agent.response.MyOpenCaseResponse(
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
     * Fetch all cases ordered by createdAt DESC.
     * Uses JOIN FETCH to load all relationships eagerly.
     */
    @Query("""
        SELECT DISTINCT c FROM Case c
        LEFT JOIN FETCH c.citizen
        LEFT JOIN FETCH c.category
        LEFT JOIN FETCH c.department
        LEFT JOIN FETCH c.createdByUser
        LEFT JOIN FETCH c.assignedToUser
        ORDER BY c.createdAt DESC
        """)
    List<Case> findAllCasesForReport();

    /**
     * Fetch cases created on or after a given date.
     */
    @Query("""
        SELECT DISTINCT c FROM Case c
        LEFT JOIN FETCH c.citizen
        LEFT JOIN FETCH c.category
        LEFT JOIN FETCH c.department
        LEFT JOIN FETCH c.createdByUser
        LEFT JOIN FETCH c.assignedToUser
        WHERE c.createdAt >= :startDate
        ORDER BY c.createdAt DESC
        """)
    List<Case> findCasesForReportAfter(@Param("startDate") OffsetDateTime startDate);

    /**
     * Fetch cases created before a given date.
     */
    @Query("""
        SELECT DISTINCT c FROM Case c
        LEFT JOIN FETCH c.citizen
        LEFT JOIN FETCH c.category
        LEFT JOIN FETCH c.department
        LEFT JOIN FETCH c.createdByUser
        LEFT JOIN FETCH c.assignedToUser
        WHERE c.createdAt < :endDate
        ORDER BY c.createdAt DESC
        """)
    List<Case> findCasesForReportBefore(@Param("endDate") OffsetDateTime endDate);

    /**
     * Fetch cases within a createdAt date range, ordered by createdAt DESC.
     */
    @Query("""
        SELECT DISTINCT c FROM Case c
        LEFT JOIN FETCH c.citizen
        LEFT JOIN FETCH c.category
        LEFT JOIN FETCH c.department
        LEFT JOIN FETCH c.createdByUser
        LEFT JOIN FETCH c.assignedToUser
        WHERE c.createdAt >= :startDate AND c.createdAt < :endDate
        ORDER BY c.createdAt DESC
        """)
    List<Case> findCasesForReportBetween(@Param("startDate") OffsetDateTime startDate,
                                         @Param("endDate") OffsetDateTime endDate);
}
