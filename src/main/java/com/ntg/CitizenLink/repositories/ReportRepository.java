package com.ntg.CitizenLink.repositories;

import com.ntg.CitizenLink.entities.Case;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dedicated repository for report-specific queries (US-27).
 * Kept separate from CaseRepository to avoid that class growing
 * unboundedly — report queries tend to be heavy aggregations that
 * don't belong alongside single-entity CRUD operations.
 */
@Repository
public interface ReportRepository extends JpaRepository<Case, UUID> {

    /**
     * RPT-01: Cases created per calendar day within the date range.
     * Returns Object[]{date (LocalDate), count (Long)}.
     */
    @Query("""
        SELECT CAST(c.createdAt AS LocalDate), COUNT(c)
        FROM Case c
        WHERE c.createdAt >= :from AND c.createdAt < :to
        GROUP BY CAST(c.createdAt AS LocalDate)
        ORDER BY CAST(c.createdAt AS LocalDate) ASC
        """)
    List<Object[]> countCreatedPerDay(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /**
     * RPT-02: Cases resolved per calendar day within the date range.
     * Uses resolvedAt (set by CaseServiceImpl on RESOLVE transition).
     * Returns Object[]{date (LocalDate), count (Long)}.
     */
    @Query("""
        SELECT CAST(c.resolvedAt AS LocalDate), COUNT(c)
        FROM Case c
        WHERE c.resolvedAt IS NOT NULL
          AND c.resolvedAt >= :from AND c.resolvedAt < :to
        GROUP BY CAST(c.resolvedAt AS LocalDate)
        ORDER BY CAST(c.resolvedAt AS LocalDate) ASC
        """)
    List<Object[]> countResolvedPerDay(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /**
     * RPT-04: Top 5 categories by case count within the date range.
     * Returns Object[]{nameEn (String), nameAr (String), count (Long)}.
     */
    @Query("""
        SELECT cat.nameEn, cat.nameAr, COUNT(c)
        FROM Case c JOIN c.category cat
        WHERE c.createdAt >= :from AND c.createdAt < :to
        GROUP BY cat.nameEn, cat.nameAr
        ORDER BY COUNT(c) DESC
        """)
    List<Object[]> countTopCategories(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            org.springframework.data.domain.Pageable pageable);
}