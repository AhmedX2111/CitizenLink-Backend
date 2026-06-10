package com.ntg.CitizenLink.repositories;


import com.ntg.CitizenLink.entities.Case;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

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
     * Used inside CaseService to verify the generated number is unique
     * (extremely unlikely to collide, but verified for safety).
     */
    boolean existsByCaseNumber(String caseNumber);
}
