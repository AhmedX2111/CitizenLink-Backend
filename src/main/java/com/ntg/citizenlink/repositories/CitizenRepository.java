package com.ntg.citizenlink.repositories;


import com.ntg.citizenlink.entities.Citizen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface CitizenRepository extends JpaRepository<Citizen, UUID> {

    boolean existsByNationalId(String nationalId);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    Optional<Citizen> findByNationalId(String nationalId);

    Optional<Citizen> findByPhone(String phone);

    // Search by partial normalized name, full national ID, or normalized phone
    @Query("SELECT c FROM Citizen c WHERE " +
            "LOWER(c.fullNameNormalized) LIKE CONCAT('%', :normalizedTerm, '%') OR " +
            "c.nationalId = :searchTerm OR " +
            "c.phone = :phoneTerm")
    Page<Citizen> searchCitizens(@Param("normalizedTerm") String normalizedTerm,
                                  @Param("searchTerm") String searchTerm,
                                  @Param("phoneTerm") String phoneTerm,
                                  Pageable pageable);

    /**
     * Returns case counts grouped by citizen, for ALL citizen IDs given at once.
     * Replaces the old per-citizen countCasesByCitizenId() to avoid N+1 queries
     * when building a page of CitizenResponse — one query covers the whole page.
     *
     * Each row is Object[]{citizenId (UUID), count (Long)}.
     * Citizens with zero cases are simply absent from the result —
     * the caller must default missing IDs to 0.
     */
    @Query("SELECT c.citizen.id, COUNT(c) FROM Case c " +
            "WHERE c.citizen.id IN :citizenIds " +
            "GROUP BY c.citizen.id")
    List<Object[]> countCasesByCitizenIds(@Param("citizenIds") List<UUID> citizenIds);
}
