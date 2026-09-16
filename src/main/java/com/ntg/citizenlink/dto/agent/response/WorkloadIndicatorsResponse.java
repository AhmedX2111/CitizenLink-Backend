package com.ntg.citizenlink.dto.agent.response;

import java.util.List;

/**
 * US-54 (DSH-04, DSH-05): role-aware workload indicators for the dashboard,
 * GET /api/v1/dashboard/workload.
 *
 * scope      — PERSONAL (HANDLER: own workload) or TEAM (SUPERVISOR: whole
 *              queue). Counts are always computed over the caller's
 *              permitted cases.
 * indicators — one entry per card. {@code link} is the API path (with the
 *              matching filter applied) that lists exactly the cases behind
 *              the count: /api/v1/dashboard/my-inbox for handlers,
 *              /api/v1/cases with the matching quick filter for supervisors.
 */
public record WorkloadIndicatorsResponse(
        Scope scope,
        List<Indicator> indicators
) {

    public enum Scope { PERSONAL, TEAM }

    public enum Key {
        /** HANDLER only — live (non-final) cases assigned to the handler. */
        ASSIGNED,
        /** dueAt in the past, still live work. */
        OVERDUE,
        /** dueAt inside the current calendar day (app time zone). */
        DUE_TODAY,
        /** SUPERVISOR only — cases with no assignee, still live. */
        UNASSIGNED
    }

    public record Indicator(
            Key key,
            long count,
            /** API path with the matching filter query applied. */
            String link
    ) {}
}
