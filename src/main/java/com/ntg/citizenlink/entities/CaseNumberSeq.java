package com.ntg.citizenlink.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-year sequence counter for generating human-readable case numbers
 * in the format CASE-{YYYY}-{00001}.
 *
 * Usage in service layer (inside a transaction):
 *
 *   repo.insertIfAbsent(year);                 // INSERT ... ON CONFLICT DO NOTHING
 *   CaseNumberSeq seq = repo.findByYearForUpdate(year)  // SELECT ... FOR UPDATE
 *       .orElseThrow();
 *   seq.setLastSeq(seq.getLastSeq() + 1);       // flushed at commit
 *
 * Never use SELECT MAX() + 1 — that has a race condition under concurrent inserts.
 * The pessimistic lock keeps both mutation and commit atomic.
 * <p>
 * The upsert is what makes the very first insert of a year race-safe: before
 * the row exists there is nothing to lock, so instead of saving a new entity
 * (which can duplicate the primary key under concurrency) the row is created
 * with ON CONFLICT DO NOTHING and then re-selected under the pessimistic lock.
 */
@Entity
@Table(name = "case_number_seq")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CaseNumberSeq {

    /**
     * Calendar year is the primary key (e.g. 2026).
     * A new row is inserted automatically when the first case of a new year is created.
     */
    @Id
    @Column(name = "year", nullable = false, updatable = false)
    private int year;

    @Column(name = "last_seq", nullable = false)
    private int lastSeq = 0;

    public CaseNumberSeq(int year) {
        this.year = year;
    }
}
