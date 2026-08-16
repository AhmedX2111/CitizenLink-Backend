package com.ntg.citizenlink.integration;

import com.ntg.citizenlink.repositories.CaseNumberSeqRepository;
import com.ntg.citizenlink.service.interfaces.CaseNumberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
 *
 * Also guards the year-rollover race (M-02): concurrent first-case-of-the-year
 * requests must not collide on the primary key, because the row is created via
 * INSERT ... ON CONFLICT DO NOTHING before the pessimistic-locked read.
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

    @Test
    void concurrentFirstRequests_forNewYear_allocateDenseSequence_withoutPrimaryKeyViolation() throws Exception {
        int year = Year.now().getValue();
        seqRepository.deleteAll();

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> allocated = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(30, TimeUnit.SECONDS);
                allocated.add(caseNumberService.generateNext());
                return null;
            }));
        }

        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // No request may fail with a duplicate-key violation, and the 8
        // racing "first case of the year" requests must each receive a
        // unique, dense sequence number CASE-YYYY-00001 .. 00008.
        assertThat(allocated).hasSize(threads);
        for (int i = 1; i <= threads; i++) {
            assertThat(allocated).contains(String.format("CASE-%d-%05d", year, i));
        }
        assertThat(seqRepository.findById(year).orElseThrow().getLastSeq()).isEqualTo(threads);
    }
}