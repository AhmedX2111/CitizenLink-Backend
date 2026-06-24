package com.ntg.CitizenLink.repositories;


import com.ntg.CitizenLink.entities.Citizen;
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

    // Search by partial name, full national ID, or phone
    @Query("SELECT c FROM Citizen c WHERE " +
            "LOWER(c.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "c.nationalId = :searchTerm OR " +
            "c.phone = :searchTerm")
    Page<Citizen> searchCitizens(@Param("searchTerm") String searchTerm, Pageable pageable);

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
