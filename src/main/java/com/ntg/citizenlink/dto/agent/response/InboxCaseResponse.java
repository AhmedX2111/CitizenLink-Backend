package com.ntg.citizenlink.dto.agent.response;

import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.Priority;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Row projection for US-49 "View my work inbox" — one row per open case
 * assigned to the logged-in HANDLER.
 *
 * Intentionally minimal — only the 8 columns the inbox table renders
 * (case ID, subject, citizen, priority, status, due date, last update,
 * plus the case number as the human-readable identifier). Avoids pulling
 * the full CaseResponse (category, department, description, ...) for a
 * paginated list.
 */
public record InboxCaseResponse(
        UUID id,
        String caseNumber,
        String subject,
        String citizenFullName,
        Priority priority,
        CaseStatus status,
        OffsetDateTime dueAt,
        OffsetDateTime updatedAt
) {}
