package com.ntg.CitizenLink.service;


import com.ntg.CitizenLink.entities.CaseNumberSeq;
import com.ntg.CitizenLink.repositories.CaseNumberSeqRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/**
 * Generates human-readable case numbers in the format CASE-{YYYY}-{00001}.
 *
 * Strategy:
 *   1. Acquire a pessimistic write lock on the row for the current year.
 *   2. If no row exists (first case of the year), insert one with lastSeq = 0.
 *   3. Increment lastSeq atomically.
 *   4. Format: CASE-2026-00001 (5-digit zero-padded sequence).
 *
 * Uses Propagation.REQUIRES_NEW so the sequence increment is committed
 * immediately, even if the outer case transaction rolls back. This means
 * sequence numbers may have gaps on rollback — this is intentional and correct.
 * Gaps in a business identifier are acceptable; duplicate identifiers are not.
 */
@Service
public class CaseNumberService {

    private final CaseNumberSeqRepository seqRepository;

    public CaseNumberService(CaseNumberSeqRepository seqRepository) {
        this.seqRepository = seqRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext() {
        int year = Year.now().getValue();

        // Get or create the row for this year, with a row-level write lock
        CaseNumberSeq seq = seqRepository
                .findByYearForUpdate(year)
                .orElseGet(() -> seqRepository.saveAndFlush(new CaseNumberSeq(year)));

        // Increment and flush within the REQUIRES_NEW transaction
        seqRepository.incrementSequence(year);
        seqRepository.flush();

        // Re-read the updated value
        int nextSeq = seqRepository.findByYearForUpdate(year)
                .orElseThrow()
                .getLastSeq();

        return String.format("CASE-%d-%05d", year, nextSeq);
    }
}
