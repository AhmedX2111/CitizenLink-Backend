package com.ntg.CitizenLink.dto.agent.response;

import com.ntg.CitizenLink.enums.CaseStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight projection for US-06 "My Open Cases" widget.
 * Intentionally minimal — only the 4 columns the HANDLER table needs.
 * Avoids pulling the full CaseResponse (citizen, category, department, etc.)
 * for a widget that only renders 5 rows.
 */
public record MyOpenCaseResponse(
        UUID id,
        String caseNumber,
        String subject,
        CaseStatus status,
        OffsetDateTime dueAt
) {}