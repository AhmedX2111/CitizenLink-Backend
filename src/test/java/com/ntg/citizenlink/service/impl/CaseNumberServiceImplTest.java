package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.entities.CaseNumberSeq;
import com.ntg.citizenlink.repositories.CaseNumberSeqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CaseNumberServiceImpl} — the per-year sequence
 * generator behind human-readable case numbers (CASE-YYYY-NNNNN).
 *
 * The service first seeds the row with a conflict-tolerant
 * {@link CaseNumberSeqRepository#insertIfAbsent(int)} upsert, then increments
 * the invisibly-locked entity returned by
 * {@link CaseNumberSeqRepository#findByYearForUpdate(int)} in place. Covers:
 * first-case-of-the-year (upsert seeds a fresh sequence row), existing year
 * (reuses the row), correct zero-padded formatting, and consecutive
 * generations rolling through 00001, 00002, 00003.
 */
@ExtendWith(MockitoExtension.class)
class CaseNumberServiceImplTest {

    @Mock private CaseNumberSeqRepository seqRepository;

    @InjectMocks private CaseNumberServiceImpl service;

    @Test
    void shouldUpsertRow_thenProduceCaseNumber_00001_whenYearIsNew() {
        int year = 2026;
        CaseNumberSeq seeded = new CaseNumberSeq(year);
        seeded.setLastSeq(0);

        when(seqRepository.findByYearForUpdate(year)).thenReturn(Optional.of(seeded));

        String caseNumber = service.generateNext();

        assertThat(caseNumber).isEqualTo("CASE-" + year + "-00001");
        assertThat(seeded.getLastSeq()).isEqualTo(1);
        verify(seqRepository).insertIfAbsent(year);
        verify(seqRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldReuseExistingSequenceRow_andIncrementInPlace_whenYearExists() {
        int year = 2026;
        CaseNumberSeq existing = new CaseNumberSeq(year);
        existing.setLastSeq(10);

        when(seqRepository.findByYearForUpdate(year)).thenReturn(Optional.of(existing));

        String caseNumber = service.generateNext();

        assertThat(caseNumber).isEqualTo("CASE-" + year + "-00011");
        assertThat(existing.getLastSeq()).isEqualTo(11);
        verify(seqRepository).insertIfAbsent(year);
        verify(seqRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldFormatZeroPaddedSequence_forHighNumbers() {
        int year = 2026;
        CaseNumberSeq existing = new CaseNumberSeq(year);
        existing.setLastSeq(12345);

        when(seqRepository.findByYearForUpdate(year)).thenReturn(Optional.of(existing));

        assertThat(service.generateNext()).isEqualTo("CASE-" + year + "-12346");
        verify(seqRepository).insertIfAbsent(year);
    }

    @Test
    void shouldGenerateConsecutiveNumbers_acrossCalls() {
        int year = 2026;
        CaseNumberSeq existing = new CaseNumberSeq(year);
        existing.setLastSeq(0);

        when(seqRepository.findByYearForUpdate(year)).thenReturn(Optional.of(existing));

        assertThat(service.generateNext()).isEqualTo("CASE-" + year + "-00001");
        assertThat(service.generateNext()).isEqualTo("CASE-" + year + "-00002");
        assertThat(service.generateNext()).isEqualTo("CASE-" + year + "-00003");
        verify(seqRepository, times(3)).insertIfAbsent(year);
        verify(seqRepository, never()).saveAndFlush(any());
    }
}