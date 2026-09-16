package com.ntg.citizenlink.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/**
 * Single source of truth for the application time zone (app.time-zone).
 *
 * US-54: the dashboard workload indicators and the case-list filters they
 * link to (overdue / dueToday / unassigned) must agree on what a calendar
 * day is, so both DashboardServiceImpl and CaseServiceImpl resolve "today"
 * through this component instead of each applying its own fallback.
 * Blank/unset falls back to the JVM default zone — the same behaviour the
 * inbox (US-50) and the case-number year logic already implement.
 */
@Component
public class AppTimeZone {

    private final ZoneId zoneId;

    public AppTimeZone(@Value("${app.time-zone:}") String timeZone) {
        this.zoneId = (timeZone == null || timeZone.isBlank())
                ? ZoneId.systemDefault()
                : ZoneId.of(timeZone);
    }

    public ZoneId zoneId() {
        return zoneId;
    }
}
