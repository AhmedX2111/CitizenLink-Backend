package com.ntg.CitizenLink.repositories;


import com.ntg.CitizenLink.entities.CaseNumberSeq;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CaseNumberSeqRepository extends JpaRepository<CaseNumberSeq, Integer> {

    /**
     * Atomically increments the sequence counter for the given year
     * and returns the new value. Must be called inside a transaction.
     *
     * This is safe under concurrent inserts because the UPDATE acquires
     * a row-level lock before returning the incremented value.
     * DO NOT replace with SELECT MAX() + 1 — that has a race condition.
     */
    @Modifying
    @Query("UPDATE CaseNumberSeq s SET s.lastSeq = s.lastSeq + 1 WHERE s.year = :year")
    int incrementSequence(@Param("year") int year);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CaseNumberSeq s WHERE s.year = :year")
    Optional<CaseNumberSeq> findByYearForUpdate(@Param("year") int year);
}
