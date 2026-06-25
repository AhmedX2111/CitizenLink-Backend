package com.ntg.CitizenLink.repositories;

import com.ntg.CitizenLink.entities.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Get all attachments for a case, ordered by newest first
     */
    @Query("SELECT a FROM Attachment a WHERE a.caseEntity.id = :caseId ORDER BY a.createdAt DESC")
    List<Attachment> findByCaseEntityIdOrderByCreatedAtDesc(@Param("caseId") UUID caseId);

    /**
     * Count attachments for a case
     */
    long countByCaseEntityId(UUID caseId);

    /**
     * Delete all attachments for a case (for case deletion)
     */
    void deleteByCaseEntityId(UUID caseId);
}
