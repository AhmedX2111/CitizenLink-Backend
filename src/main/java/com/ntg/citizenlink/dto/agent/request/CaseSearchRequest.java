package com.ntg.citizenlink.dto.agent.request;

import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.CaseType;
import com.ntg.citizenlink.enums.Priority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Query parameters for GET /api/v1/cases.
 *
 * All filters are optional. Combined with AND logic.
 * Pagination defaults: page=0, size=20.
 * Sorting is always createdAt DESC (not client-configurable at this stage).
 *
 * Visibility rule (Phase 1): results are restricted to cases where
 * createdByUser = authenticated user. This restriction lives in
 * CaseService, not here — this DTO carries only filter values.
 */
@Setter
@Getter
public class CaseSearchRequest {

    /** Filter by case status. */
    private CaseStatus status;

    /** Filter by case type (COMPLAINT | REQUEST). */
    private CaseType type;

    /** Filter by priority. */
    private Priority priority;

    /** Filter by assigned handler UUID. */
    private UUID assignedToUserId;

    /**
     * Keyword search: matches against case_number (exact prefix) OR
     * subject (case-insensitive LIKE %keyword%).
     */
    private String keyword;

    @Min(value = 0, message = "Page index must be >= 0")
    private int page = 0;

    @Min(value = 1, message = "Page size must be >= 1")
    @Max(value = 100, message = "Page size must be <= 100")
    private int size = 20;
}
