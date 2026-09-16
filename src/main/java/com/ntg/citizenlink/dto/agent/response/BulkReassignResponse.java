package com.ntg.citizenlink.dto.agent.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * US-53: outcome of a bulk reassignment.
 *
 * The batch is best-effort: eligible cases are reassigned, ineligible ones
 * (closed, cancelled, NEW, RESOLVED, ...) are rejected. Every requested
 * case ID appears exactly once in {@code results} with success=true or a
 * concrete error code — failures are never silently skipped. The overall
 * HTTP status stays 200 when the request itself was well-formed; the
 * per-case results carry the partial-failure detail.
 */
@Getter
@Setter
public class BulkReassignResponse {

    private int totalRequested;
    private int succeeded;
    private int failed;
    private List<CaseResult> results = new ArrayList<>();

    /**
     * Per-case outcome. On failure, errorCode/message explain why the case
     * was NOT reassigned (e.g. INVALID_TRANSITION for closed/cancelled
     * cases, NOT_FOUND for unknown IDs). On success both are null.
     */
    @Getter
    @Setter
    public static class CaseResult {

        private UUID caseId;
        private String caseNumber;
        private boolean success;
        private String errorCode;
        private String message;
    }
}
