package com.ntg.CitizenLink.repositories;


import com.ntg.CitizenLink.entities.CaseNumberSeq;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CaseNumberSeqRepository extends JpaRepository<CaseNumberSeq, Integer> {

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
