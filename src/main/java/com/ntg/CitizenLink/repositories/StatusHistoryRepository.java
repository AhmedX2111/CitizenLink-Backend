package com.ntg.CitizenLink.repositories;


import com.ntg.CitizenLink.entities.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Append-only repository. Never call deleteById or deleteAll from service layer.
 */
@Repository
public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {
}
