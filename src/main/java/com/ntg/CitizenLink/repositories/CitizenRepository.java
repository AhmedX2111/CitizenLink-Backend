package com.ntg.CitizenLink.repositories;


import com.ntg.CitizenLink.entities.Citizen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CitizenRepository extends JpaRepository<Citizen, UUID> {
    boolean existsByNationalId(String nationalId);
}
