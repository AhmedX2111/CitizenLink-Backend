package com.ntg.citizenlink.dto.agent.response;

import java.util.Map;

/**
 * Response body for GET /api/v1/dashboard/summary (US-04, US-05).
 *
 * kpis        — the four KPI card values (US-04)
 * statusCounts — count of cases grouped by every CaseStatus value (US-05).
 *                Includes statuses with zero cases so the frontend chart
 *                always renders all categories consistently.
 */
public record DashboardSummaryResponse(
        KpiSummary kpis,
        Map<String, Long> statusCounts
) {
    public record KpiSummary(
            long openCases,
            long resolvedThisMonth,
            long overdueCases,
            long newToday
    ) {}
}