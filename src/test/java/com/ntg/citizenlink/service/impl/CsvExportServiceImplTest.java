package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.CaseType;
import com.ntg.citizenlink.enums.Channel;
import com.ntg.citizenlink.enums.Priority;
import com.ntg.citizenlink.repositories.CaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvExportServiceImplTest {

    @Mock
    private CaseRepository caseRepository;

    @InjectMocks
    private CsvExportServiceImpl csvExportService;

    @Test
    void exportCasesCsv_neutralizesFormulaInjectionPrefixes() throws Exception {
        Case c = caseWith("CASE-2026-00001");
        c.setSubject("=cmd|'/c calc'!A1");
        c.setDescription("+1+2");
        c.setResolutionSummary("@SUM(A1:A2)");
        mockSinglePage(c);

        String csv = csv(null, null);

        assertThat(csv).contains("'=cmd|'/c calc'!A1");
        assertThat(csv).contains("'+1+2");
        assertThat(csv).contains("'@SUM(A1:A2)");
    }

    @Test
    void exportCasesCsv_keepsNormalValuesUnchanged() throws Exception {
        Case c = caseWith("CASE-2026-00002");
        c.setSubject("normal subject");
        c.setDescription("plain text");
        mockSinglePage(c);

        String csv = csv(null, null);

        assertThat(csv).contains("normal subject");
        assertThat(csv).contains("plain text");
        assertThat(csv).doesNotContain("'normal");
        assertThat(csv).doesNotContain("'plain");
    }

    @Test
    void exportCasesCsv_quotesCommaValues() throws Exception {
        Case c = caseWith("CASE-2026-00003");
        c.setSubject("with, comma");
        c.setDescription("=SUM(A1:A2) with, comma");
        mockSinglePage(c);

        String csv = csv(null, null);

        assertThat(csv).contains("\"with, comma\"");
        assertThat(csv).contains("\"'=SUM(A1:A2) with, comma\"");
    }

    @Test
    void exportCasesCsv_streamsAcrossPages_emittingEachRowOnce() throws Exception {
        Case c1 = caseWith("CASE-2026-00001");
        Case c2 = caseWith("CASE-2026-00002");
        Case c3 = caseWith("CASE-2026-00003");

        when(caseRepository.findCasesForReportBetween(any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(c1, c2), PageRequest.of(0, 1000), true))
                .thenReturn(new SliceImpl<>(List.of(c3), PageRequest.of(1, 1000), false));

        String csv = csv(null, null);

        assertThat(occurrences(csv, "CASE-2026-00001")).isEqualTo(1);
        assertThat(occurrences(csv, "CASE-2026-00002")).isEqualTo(1);
        assertThat(occurrences(csv, "CASE-2026-00003")).isEqualTo(1);
    }

    @Test
    void exportCasesCsv_withNullDates_defaultsToBoundedWindow() throws Exception {
        when(caseRepository.findCasesForReportBetween(any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 1000), false));

        csv(null, null);

        ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(caseRepository).findCasesForReportBetween(startCaptor.capture(), endCaptor.capture(), any());

        OffsetDateTime start = startCaptor.getValue();
        OffsetDateTime end = endCaptor.getValue();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        assertThat(start).isBeforeOrEqualTo(now);
        assertThat(start).isAfter(now.minusDays(31));
        assertThat(end).isAfter(now.minusMinutes(5));
        assertThat(ChronoUnit.DAYS.between(start, end)).isLessThanOrEqualTo(30);
    }

    @Test
    void exportCasesCsv_rejectsStartAfterEnd() throws Exception {
        OffsetDateTime start = OffsetDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        assertThatThrownBy(() -> csv(start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start date must not be after end date");
        verify(caseRepository, never()).findCasesForReportBetween(any(), any(), any());
    }

    @Test
    void exportCasesCsv_rejectsSpanBeyondMax() throws Exception {
        OffsetDateTime start = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        assertThatThrownBy(() -> csv(start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum allowed span");
        verify(caseRepository, never()).findCasesForReportBetween(any(), any(), any());
    }

    private void mockSinglePage(Case c) {
        when(caseRepository.findCasesForReportBetween(any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(c), PageRequest.of(0, 1000), false));
    }

    private static Case caseWith(String caseNumber) {
        Case c = new Case();
        c.setCaseNumber(caseNumber);
        c.setSubject("subject");
        c.setDescription("description");
        c.setType(CaseType.COMPLAINT);
        c.setPriority(Priority.HIGH);
        c.setStatus(CaseStatus.NEW);
        c.setChannel(Channel.WEB);
        return c;
    }

    private String csv(OffsetDateTime start, OffsetDateTime end) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        csvExportService.exportCasesCsv(bos, start, end);
        return bos.toString(StandardCharsets.UTF_8);
    }

    private static int occurrences(String text, String needle) {
        return text.split(needle, -1).length - 1;
    }
}
