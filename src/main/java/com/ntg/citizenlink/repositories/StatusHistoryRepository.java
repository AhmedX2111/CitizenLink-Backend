package com.ntg.citizenlink.repositories;


import com.ntg.citizenlink.entities.StatusHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Append-only repository. Never call deleteById or deleteAll from service layer.
 */
@Repository
public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {
    /**
     * Returns the full status history for a case, oldest first —
     * chronological order, exactly what a timeline UI needs (US-14, DET-03).
     *
     * M-18: the entity graph loads changedByUser in the same query so mapping
     * the timeline does not trigger one lazy select per history row.
     */
    @EntityGraph(attributePaths = {"changedByUser"})
    @Query("SELECT sh FROM StatusHistory sh " +
           "WHERE sh.caseEntity.id = :caseId " +
           "ORDER BY sh.createdAt ASC")
    List<StatusHistory> findByCaseIdOrderByCreatedAtAsc(@Param("caseId") UUID caseId);
}
