package com.ntg.CitizenLink.entities;

import jakarta.persistence.*;

/**
 * Per-year sequence counter for generating human-readable case numbers
 * in the format CASE-{YYYY}-{00001}.
 *
 * Usage in service layer (inside a transaction):
 *
 *   UPDATE case_number_seq SET last_seq = last_seq + 1 WHERE year = :year RETURNING last_seq
 *
 * Never use SELECT MAX() + 1 — that has a race condition under concurrent inserts.
 * The UPDATE ... RETURNING pattern is atomic and safe.
 */
@Entity
@Table(name = "case_number_seq")
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

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    protected CaseNumberSeq() {}

    public CaseNumberSeq(int year) {
        this.year = year;
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public int getYear() { return year; }

    public int getLastSeq() { return lastSeq; }
    public void setLastSeq(int lastSeq) { this.lastSeq = lastSeq; }
}
