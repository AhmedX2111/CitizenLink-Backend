package com.ntg.citizenlink.constants;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Single source of truth for the bounded date-range rules shared by the report
 * and export endpoints (M-16). Unbounded ranges (e.g. {@code from=0001-01-01}
 * {@code to=9999-12-31}) would let a single authenticated request materialise
 * millions of rows, so every range is validated before any work happens.
 */
public final class DateRangeValidator {

    /** Maximum allowed span (inclusive day count semantics match the CSV export). */
    public static final long MAX_RANGE_DAYS = 366;

    private DateRangeValidator() {
        // utility class — prevent instantiation
    }

    /**
     * @throws IllegalArgumentException when {@code from} is after {@code to} or
     *                                  the span exceeds {@link #MAX_RANGE_DAYS}
     */
    public static void validate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Start date must not be after end date");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "Requested date range exceeds the maximum allowed span of " + MAX_RANGE_DAYS + " days");
        }
    }

    /**
     * @throws IllegalArgumentException when {@code start} is after {@code end}
     *                                  or the span exceeds {@link #MAX_RANGE_DAYS}
     */
    public static void validate(OffsetDateTime start, OffsetDateTime end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must not be after end date");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "Requested date range exceeds the maximum allowed span of " + MAX_RANGE_DAYS + " days");
        }
    }
}
