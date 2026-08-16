package com.ntg.citizenlink.repositories;


import com.ntg.citizenlink.entities.CaseNumberSeq;
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
     * Conflict-tolerant insert of the sequence row for a year that does not
     * exist yet (the first case of a new year). Runs a native
     * {@code INSERT INTO case_number_seq (year, last_seq) VALUES ... ON CONFLICT DO NOTHING}.
     * <p>
     * This is race-safe for the year-rollover case: {@link #findByYearForUpdate(int)}
     * can only lock a row that already exists, so two requests arriving
     * simultaneously for a brand-new year cannot both insert the primary key.
     * Exactly one request creates the row; the others are silently ignored.
     * <p>
     * Callers MUST invoke {@link #findByYearForUpdate(int)} afterwards, in the
     * same transaction, to lock the now-guaranteed row before mutating it.
     */
    @Modifying
    @Query(value = "INSERT INTO case_number_seq (year, last_seq) VALUES (:year, 0) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("year") int year);

    /**
     * Loads the sequence counter for the given year with a pessimistic write
     * lock (SELECT ... FOR UPDATE), so concurrent generators serialize on the
     * row. Callers must mutate the returned managed entity inside the
     * transaction; the incremented value is flushed at commit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CaseNumberSeq s WHERE s.year = :year")
    Optional<CaseNumberSeq> findByYearForUpdate(@Param("year") int year);
}
