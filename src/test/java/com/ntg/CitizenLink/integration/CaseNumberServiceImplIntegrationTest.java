package com.ntg.CitizenLink.integration;

import com.ntg.CitizenLink.repositories.CaseNumberSeqRepository;
import com.ntg.CitizenLink.service.interfaces.CaseNumberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link CaseNumberService} against a real database (H2).
 *
 * Uses a dedicated in-memory database so it cannot corrupt the shared test DB
 * (the sequence counter must not be reset under other integration tests).
 *
 * Guards against the off-by-one regression where the first case of a year was
 * numbered 00000 and every subsequent case was one lower than the stored
 * sequence counter.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:citizenlink-casenumber-it;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR")
class CaseNumberServiceImplIntegrationTest {

    @Autowired private CaseNumberService caseNumberService;
    @Autowired private CaseNumberSeqRepository seqRepository;

    @BeforeEach
    void setUp() {
        seqRepository.deleteAll();
    }

    @Test
    void generatesSequentialNumbers_startingFromOne_forCurrentYear() {
        int year = Year.now().getValue();

        assertThat(caseNumberService.generateNext()).isEqualTo(String.format("CASE-%d-00001", year));
        assertThat(caseNumberService.generateNext()).isEqualTo(String.format("CASE-%d-00002", year));
        assertThat(caseNumberService.generateNext()).isEqualTo(String.format("CASE-%d-00003", year));
    }

    @Test
    void generatedNumbersMatchStoredCounter_afterEachCall() {
        int year = Year.now().getValue();

        caseNumberService.generateNext();
        caseNumberService.generateNext();

        int stored = seqRepository.findById(year).orElseThrow().getLastSeq();
        assertThat(stored).isEqualTo(2);
    }
}