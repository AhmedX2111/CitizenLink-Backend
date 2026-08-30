package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.dto.agent.response.DashboardSummaryResponse;
import com.ntg.citizenlink.dto.agent.response.InboxCaseResponse;
import com.ntg.citizenlink.dto.agent.response.InboxCountsResponse;
import com.ntg.citizenlink.dto.agent.response.MyOpenCaseResponse;
import com.ntg.citizenlink.dto.agent.response.PagedResponse;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.InboxSort;
import com.ntg.citizenlink.enums.Priority;
import com.ntg.citizenlink.security.config.SecurityContextHelper;
import com.ntg.citizenlink.service.interfaces.DashboardService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Dashboard endpoints backing US-04, US-05, US-06, US-49, US-50 and US-51.
 *
 *   GET /api/v1/dashboard/summary         — KPI cards + status chart (any authenticated user)
 *   GET /api/v1/dashboard/my-open-cases   — HANDLER-only widget (US-06)
 *   GET /api/v1/dashboard/my-inbox        — HANDLER-only paged work inbox (US-49/50/51)
 *   GET /api/v1/dashboard/my-inbox/counts — HANDLER-only quick-filter badge counts (US-50)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * US-04 + US-05.
     * Accessible to any authenticated role — KPI cards and the status chart
     * are relevant to AGENT, HANDLER, SUPERVISOR, and ADMIN alike.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('AGENT', 'HANDLER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    /**
     * US-06. Restricted to HANDLER only per acceptance criteria.
     * "assignedToUser = current user" — the current authenticated HANDLER.
     */
    @GetMapping("/my-open-cases")
    @PreAuthorize("hasRole('HANDLER')")
    public ResponseEntity<List<MyOpenCaseResponse>> getMyOpenCases() {
        UUID userId = securityContextHelper.getAuthenticatedUserId();
        return ResponseEntity.ok(dashboardService.getMyOpenCases(userId));
    }

    /**
     * US-49 + US-50 + US-51. HANDLER-only paged work inbox — the open cases
     * assigned to the current handler, server-side filtered, sorted and
     * paginated. All filter/sort params are optional so the frontend can
     * round-trip them as URL query state (US-52).
     *
     * Params:
     *   status     — explicit status filter; overrides the default final-state
     *                exclusion (CLOSED, CANCELLED) that applies when absent
     *                (documented behaviour).
     *   priority   — exact-match priority filter.
     *   keyword    — matches case-number prefix OR subject, case-insensitive,
     *                wildcard-escaped.
     *   overdue    — US-50 quick filter. TRUE keeps only cases with
     *                dueAt != null AND dueAt &lt; now. Absent or FALSE applies
     *                no overdue filtering.
     *   dueToday   — US-50 quick filter. TRUE keeps only cases with
     *                todayStart &lt;= dueAt &lt; todayEnd, where "today" is
     *                resolved in the app time zone (app.time-zone — the same
     *                mechanism the case-number year logic uses). Absent or
     *                FALSE applies no due-today filtering.
     *   sort       — US-51 ordering: SMART (default; overdue first, then
     *                priority rank URGENT&gt;HIGH&gt;MEDIUM&gt;LOW, then
     *                nearest due date nulls last), DUE_DATE, PRIORITY, NEWEST.
     *   page/size  — bounds mirror CaseSearchRequest validation
     *                (page &gt;= 0, 1 &lt;= size &lt;= 100).
     *
     * Composed semantics: overdue and dueToday are independent ANDed
     * predicates. For due dates strictly before today, or inside today but
     * not yet past, they are mutually exclusive — so overdue=true&amp;dueToday=
     * true typically yields an empty page. Edge case: a case due EARLIER
     * TODAY (dueAt already in the past but still inside today's window)
     * satisfies both formulas, and such cases are the only rows
     * overdue=true&amp;dueToday=true can return. The default final-state
     * exclusion keeps working under the new dimensions: overdue=true without
     * an explicit status never returns a CLOSED case, while
     * status=CLOSED&amp;overdue=true explicitly overrides the default and
     * lists closed cases past due.
     */
    @GetMapping("/my-inbox")
    @PreAuthorize("hasRole('HANDLER')")
    public ResponseEntity<PagedResponse<InboxCaseResponse>> getMyInbox(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page index must be >= 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be >= 1")
            @Max(value = 100, message = "Page size must be <= 100") int size,
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Boolean dueToday,
            @RequestParam(required = false) InboxSort sort) {

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        return ResponseEntity.ok(
                dashboardService.getMyInbox(userId, status, priority, keyword,
                        overdue, dueToday, sort, page, size));
    }

    /**
     * US-50. HANDLER-only quick-filter badge counts for the caller's inbox.
     * Every count is computed over the handler's permitted cases
     * (assignedToUser = current handler) with the US-49 default final-state
     * exclusion (CLOSED/CANCELLED always excluded from every count), so each
     * number matches what the corresponding quick filter on /my-inbox
     * returns:
     *
     *   all            — live (non-final) assigned cases
     *   overdue        — dueAt &lt; now
     *   dueToday       — today's calendar day in the app time zone
     *   urgent         — priority = URGENT
     *   awaitingInfo   — status = AWAITING_INFO
     *   newlyAssigned  — status = ASSIGNED
     *
     * The dimensions overlap by design (an URGENT case due later today counts
     * under all, dueToday, urgent and newlyAssigned), so the values need not
     * sum to all.
     */
    @GetMapping("/my-inbox/counts")
    @PreAuthorize("hasRole('HANDLER')")
    public ResponseEntity<InboxCountsResponse> getMyInboxCounts() {
        UUID userId = securityContextHelper.getAuthenticatedUserId();
        return ResponseEntity.ok(dashboardService.getMyInboxCounts(userId));
    }
}