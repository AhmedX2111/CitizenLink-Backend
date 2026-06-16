package com.ntg.CitizenLink.repositories;


import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.enums.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    @Query("SELECT c FROM Case c WHERE c.citizen.id = :citizenId ORDER BY c.createdAt DESC")
    List<Case> findTop5ByCitizenIdOrderByCreatedAtDesc(@Param("citizenId") UUID citizenId);
}
