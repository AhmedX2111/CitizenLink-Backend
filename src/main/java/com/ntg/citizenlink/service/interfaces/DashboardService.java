package com.ntg.citizenlink.service.interfaces;

import com.ntg.citizenlink.dto.agent.response.DashboardSummaryResponse;
import com.ntg.citizenlink.dto.agent.response.InboxCaseResponse;
import com.ntg.citizenlink.dto.agent.response.InboxCountsResponse;
import com.ntg.citizenlink.dto.agent.response.MyOpenCaseResponse;
import com.ntg.citizenlink.dto.agent.response.PagedResponse;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.InboxSort;
import com.ntg.citizenlink.enums.Priority;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;


@Service
public interface DashboardService {

    /**
     * Get dashboard summary with KPIs and status chart data.
     */
    DashboardSummaryResponse getSummary();

    /**
     * Get top 5 open cases assigned to the given user.
     */
    List<MyOpenCaseResponse> getMyOpenCases(UUID userId);

    /**
     * US-49: paged work inbox for the given HANDLER — the open cases assigned
     * to them (final states excluded by default, explicit status overrides).
     *
     * US-50 extends it with the urgency quick filters; US-51 with a
     * selectable server-side ordering.
     *
     * @param userId       the authenticated HANDLER (inbox owner)
     * @param status       optional explicit status filter — overrides the
     *                     default final-state exclusion when present
     * @param priority     optional exact-match priority filter
     * @param keyword      optional keyword matched against case number prefix
     *                     or subject (case-insensitive, wildcard-escaped)
     * @param overdue      US-50 quick filter — TRUE keeps only cases with
     *                     dueAt != null AND dueAt &lt; now; absent/FALSE
     *                     applies no overdue filtering
     * @param dueToday     US-50 quick filter — TRUE keeps only cases with
     *                     todayStart &lt;= dueAt &lt; todayEnd, "today"
     *                     computed in the app time zone; absent/FALSE applies
     *                     no due-today filtering
     * @param sort         US-51 ordering; null falls back to the SMART
     *                     default (overdue first, then priority rank, then
     *                     nearest due date)
     * @param page         zero-based page index
     * @param size         page size (validated at the controller, max 100)
     */
    PagedResponse<InboxCaseResponse> getMyInbox(UUID userId, CaseStatus status,
                                                Priority priority, String keyword,
                                                Boolean overdue, Boolean dueToday,
                                                InboxSort sort,
                                                int page, int size);

    /**
     * US-50: quick-filter badge counts for the given HANDLER's inbox —
     * computed over the same permitted cases as {@link #getMyInbox} with the
     * default final-state exclusion (CLOSED/CANCELLED excluded from every
     * count). Dimensions overlap by design (see InboxCountsResponse).
     */
    InboxCountsResponse getMyInboxCounts(UUID userId);
}