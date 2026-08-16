package com.ntg.citizenlink.service.interfaces;

public interface CaseNumberService {

    /**
     * Generates the next human-readable case number.
     * Format: CASE-{YYYY}-{00001}
     */
    String generateNext();
}