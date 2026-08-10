package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.CaseType;
import com.ntg.CitizenLink.enums.Channel;
import com.ntg.CitizenLink.enums.Priority;
import com.ntg.CitizenLink.repositories.CaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvExportServiceImplTest {

    @Mock
    private CaseRepository caseRepository;

    @InjectMocks
    private CsvExportServiceImpl csvExportService;

    @Test
    void exportCasesCsv_neutralizesFormulaInjectionPrefixes() {
        Case c = new Case();
        c.setCaseNumber("CASE-2026-00001");
        c.setSubject("=cmd|'/c calc'!A1");
        c.setDescription("+1+2");
        c.setResolutionSummary("@SUM(A1:A2)");
        c.setType(CaseType.COMPLAINT);
        c.setPriority(Priority.HIGH);
        c.setStatus(CaseStatus.NEW);
        c.setChannel(Channel.WEB);
        when(caseRepository.findAllCasesForReport()).thenReturn(List.of(c));

        String csv = csv();

        assertThat(csv).contains("'=cmd|'/c calc'!A1");
        assertThat(csv).contains("'+1+2");
        assertThat(csv).contains("'@SUM(A1:A2)");
    }

    @Test
    void exportCasesCsv_keepsNormalValuesUnchanged() {
        Case c = new Case();
        c.setCaseNumber("CASE-2026-00002");
        c.setSubject("normal subject");
        c.setDescription("plain text");
        c.setType(CaseType.COMPLAINT);
        c.setPriority(Priority.LOW);
        c.setStatus(CaseStatus.NEW);
        c.setChannel(Channel.WEB);
        when(caseRepository.findAllCasesForReport()).thenReturn(List.of(c));

        String csv = csv();

        assertThat(csv).contains("normal subject");
        assertThat(csv).contains("plain text");
        assertThat(csv).doesNotContain("'normal");
        assertThat(csv).doesNotContain("'plain");
    }

    @Test
    void exportCasesCsv_quotesCommaValues() {
        Case c = new Case();
        c.setCaseNumber("CASE-2026-00003");
        c.setSubject("with, comma");
        c.setDescription("=SUM(A1:A2) with, comma");
        c.setType(CaseType.COMPLAINT);
        c.setPriority(Priority.MEDIUM);
        c.setStatus(CaseStatus.NEW);
        c.setChannel(Channel.WEB);
        when(caseRepository.findAllCasesForReport()).thenReturn(List.of(c));

        String csv = csv();

        assertThat(csv).contains("\"with, comma\"");
        assertThat(csv).contains("\"'=SUM(A1:A2) with, comma\"");
    }

    private String csv() {
        byte[] bytes = csvExportService.exportCasesCsv(null, null);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
