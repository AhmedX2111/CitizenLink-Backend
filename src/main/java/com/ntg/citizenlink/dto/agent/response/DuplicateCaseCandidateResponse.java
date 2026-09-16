package com.ntg.citizenlink.dto.agent.response;

import com.ntg.citizenlink.enums.CaseStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * US-58: one row of the possible-duplicate list shown before a new case is
 * created from the Citizen 360 screen. Carries exactly what the warning
 * needs to display — the case id (for opening the candidate), the
 * human-readable case id, subject, status and creation date.
 */
public record DuplicateCaseCandidateResponse(
        UUID id,
        String caseNumber,
        String subject,
        CaseStatus status,
        OffsetDateTime createdAt
) {
}
