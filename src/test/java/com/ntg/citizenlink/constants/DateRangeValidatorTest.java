package com.ntg.citizenlink.constants;

import com.ntg.citizenlink.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M-16: the shared date-range policy is the single source of truth for both
 * the volume report and the CSV export. No range may be unbounded.
 */
class DateRangeValidatorTest {

    @Test
    void localDate_rejectsStartAfterEnd() {
        assertThatThrownBy(() -> DateRangeValidator.validate(
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Start date must not be after end date");
    }

    @Test
    void localDate_rejectsSpanBeyondMax() {
        assertThatThrownBy(() -> DateRangeValidator.validate(
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 5)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("maximum allowed span of 366 days");
    }

    @Test
    void localDate_acceptsMaxSpan() {
        // 2024-01-01 -> 2025-01-01 is 366 days between (2024 is a leap year).
        assertThatCode(() -> DateRangeValidator.validate(
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void offsetDateTime_rejectsStartAfterEndAndSpanBeyondMax() {
        OffsetDateTime from = OffsetDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(2026, 1, 1, 23, 59, 59, 0, ZoneOffset.UTC);
        assertThatThrownBy(() -> DateRangeValidator.validate(from, to))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Start date must not be after end date");

        OffsetDateTime start = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2025, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC);
        assertThatThrownBy(() -> DateRangeValidator.validate(start, end))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("maximum allowed span");
    }

    @Test
    void offsetDateTime_acceptsSpanWithinMax() {
        OffsetDateTime start = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        assertThatCode(() -> DateRangeValidator.validate(start, end))
                .doesNotThrowAnyException();
    }
}