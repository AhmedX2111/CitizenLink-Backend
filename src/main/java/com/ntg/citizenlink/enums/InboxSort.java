package com.ntg.citizenlink.enums;

/**
 * US-51: server-side sort options for the HANDLER work inbox
 * (GET /api/v1/dashboard/my-inbox).
 *
 * All orderings are applied INSIDE the query — InboxSpecification renders
 * them as CriteriaBuilder order items, because the overdue-first flag and
 * the priority rank are CASE expressions that Spring Data's Sort cannot
 * express (US-51 rationale). Every option ends with id ASC so pagination
 * stays stable when the leading keys tie.
 */
public enum InboxSort {

    /**
     * Default work ranking: overdue cases first, then by priority rank
     * (URGENT, HIGH, MEDIUM, LOW), then by nearest due date (nulls last).
     */
    SMART,

    /**
     * dueAt ascending with nulls LAST — the US-49 default ordering, kept
     * as an explicit alternative (a missing due date must never float to
     * the top).
     */
    DUE_DATE,

    /**
     * Priority rank (URGENT=0, HIGH=1, MEDIUM=2, LOW=3), then dueAt
     * ascending nulls last.
     */
    PRIORITY,

    /**
     * Most recently updated first (updatedAt DESC) — "what did I touch
     * last" view.
     */
    NEWEST
}
