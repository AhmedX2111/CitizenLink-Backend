package com.ntg.CitizenLink.repositories;


import com.ntg.CitizenLink.entities.Citizen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CitizenRepository extends JpaRepository<Citizen, UUID> {
    boolean existsByNationalId(String nationalId);

    Optional<Citizen> findByNationalId(String nationalId);

    Optional<Citizen> findByPhone(String phone);

    // Search by partial name, full national ID, or phone
    @Query("SELECT c FROM Citizen c WHERE " +
            "LOWER(c.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "c.nationalId = :searchTerm OR " +
            "c.phone = :searchTerm")
    Page<Citizen> searchCitizens(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Count cases for a citizen (for response)
    @Query("SELECT COUNT(case) FROM Case case WHERE case.citizen.id = :citizenId")
    long countCasesByCitizenId(@Param("citizenId") UUID citizenId);
}
