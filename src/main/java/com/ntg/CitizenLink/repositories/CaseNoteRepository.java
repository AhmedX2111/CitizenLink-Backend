package com.ntg.CitizenLink.repositories;

import com.ntg.CitizenLink.entities.CaseNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseNoteRepository extends JpaRepository<CaseNote, UUID> {

    /**
     * Get all notes for a case, ordered by newest first
     */
    @Query("SELECT n FROM CaseNote n WHERE n.caseEntity.id = :caseId ORDER BY n.createdAt DESC")
    List<CaseNote> findByCaseIdOrderByCreatedAtDesc(@Param("caseId") UUID caseId);

    /**
     * Get paginated notes for a case
     */
    @Query("SELECT n FROM CaseNote n WHERE n.caseEntity.id = :caseId ORDER BY n.createdAt DESC")
    Page<CaseNote> findByCaseIdOrderByCreatedAtDesc(@Param("caseId") UUID caseId, Pageable pageable);

    /**
     * Count notes for a case
     */
    long countByCaseEntityId(UUID caseId);

    /**
     * Delete all notes for a case (for case deletion)
     */
    void deleteByCaseEntityId(UUID caseId);
}
