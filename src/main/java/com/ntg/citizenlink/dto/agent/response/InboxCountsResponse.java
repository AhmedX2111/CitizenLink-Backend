package com.ntg.citizenlink.dto.agent.response;

/**
 * US-50: quick-filter badge counts for the HANDLER work inbox —
 * GET /api/v1/dashboard/my-inbox/counts.
 *
 * Every count is computed over the calling handler's permitted cases
 * (assignedToUser = current handler) with CLOSED/CANCELLED always excluded,
 * so each number matches what the corresponding quick filter would return
 * on /my-inbox. The dimensions deliberately overlap: a case can be counted
 * under several filters at once (an URGENT case due later today appears in
 * all, dueToday, urgent and newlyAssigned), so the values need not sum
 * to all.
 */
public record InboxCountsResponse(
        /** All live (non-final-state) cases assigned to the handler. */
        long all,
        /** dueAt is in the past (dueAt != null AND dueAt < now). */
        long overdue,
        /** dueAt falls inside the current calendar day, app time zone. */
        long dueToday,
        /** priority = URGENT. */
        long urgent,
        /** status = AWAITING_INFO. */
        long awaitingInfo,
        /** status = ASSIGNED (not yet started). */
        long newlyAssigned
) {}
