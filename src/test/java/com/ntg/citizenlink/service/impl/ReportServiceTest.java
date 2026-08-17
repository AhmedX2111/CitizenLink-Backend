package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.dto.agent.response.VolumeReportResponse;
import com.ntg.citizenlink.repositories.ReportRepository;
import com.ntg.citizenlink.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M-16: the volume report must reject unbounded or inverted date ranges
 * (400 via IllegalArgumentException) before any query or row materialisation.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ReportService reportService;

    private void stubEmptyResults() {
        when(reportRepository.countCreatedPerDay(any(), any())).thenReturn(List.of());
        when(reportRepository.countResolvedPerDay(any(), any())).thenReturn(List.of());
        when(reportRepository.countTopCategories(any(), any(), any(Pageable.class))).thenReturn(List.of());
    }

    @Test
    void getVolumeReport_rejectsStartAfterEnd() {
        assertThatThrownBy(() -> reportService.getVolumeReport(
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start date must not be after end date");

        verify(reportRepository, never()).countCreatedPerDay(any(), any());
        verify(reportRepository, never()).countResolvedPerDay(any(), any());
        verify(reportRepository, never()).countTopCategories(any(), any(), any(Pageable.class));
    }

    @Test
    void getVolumeReport_rejectsSpanBeyondMax() {
        assertThatThrownBy(() -> reportService.getVolumeReport(
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum allowed span");

        verify(reportRepository, never()).countCreatedPerDay(any(), any());
    }

    @Test
    void getVolumeReport_acceptsMaxSpan() {
        stubEmptyResults();

        // 2024-01-01 -> 2025-01-01 is the 366-day boundary (leap year 2024).
        VolumeReportResponse response = reportService.getVolumeReport(
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));

        assertThat(response.getDailyVolume()).hasSize(367);
        assertThat(response.getDailyVolume().get(0).getDate()).isEqualTo("2024-01-01");
        assertThat(response.getDailyVolume().get(366).getDate()).isEqualTo("2025-01-01");
    }

    @Test
    void getVolumeReport_returnsZeroFilledRowForEachDayInRange() {
        stubEmptyResults();

        VolumeReportResponse response = reportService.getVolumeReport(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));

        assertThat(response.getDailyVolume()).hasSize(3)
                .extracting(VolumeReportResponse.DailyVolumeRow::getDate)
                .containsExactly("2026-01-01", "2026-01-02", "2026-01-03");
        assertThat(response.getDailyVolume())
                .allSatisfy(row -> {
                    assertThat(row.getCreated()).isZero();
                    assertThat(row.getResolved()).isZero();
                });
        assertThat(response.getTopCategories()).isEmpty();
    }
}