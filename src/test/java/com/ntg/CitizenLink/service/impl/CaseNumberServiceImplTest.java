package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.entities.CaseNumberSeq;
import com.ntg.CitizenLink.repositories.CaseNumberSeqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CaseNumberServiceImpl} — the per-year sequence
 * generator behind human-readable case numbers (CASE-YYYY-NNNNN).
 *
 * The service increments the invisibly-locked entity in place, so a single
 * {@link CaseNumberSeqRepository#findByYearForUpdate(int)} lookup feeds the
 * result. Covers: first-case-of-the-year (creates a fresh sequence row),
 * existing year (reuses the row), correct zero-padded formatting, and
 * consecutive generations rolling through 00001, 00002, 00003.
 */
@ExtendWith(MockitoExtension.class)
class CaseNumberServiceImplTest {

    @Mock private CaseNumberSeqRepository seqRepository;

    @InjectMocks private CaseNumberServiceImpl service;

    @Test
    void shouldCreateNewSequenceRow_andProduceCaseNumber_00001_whenYearDoesNotExist() {
        int year = 2026;
        CaseNumberSeq created = new CaseNumberSeq(year);
        created.setLastSeq(0);

        when(seqRepository.findByYearForUpdate(year)).thenReturn(Optional.empty());
        when(seqRepository.saveAndFlush(any())).thenReturn(created);

        String caseNumber = service.generateNext();

        assertThat(caseNumber).isEqualTo("CASE-" + year + "-00001");
        assertThat(created.getLastSeq()).isEqualTo(1);
        verify(seqRepository).saveAndFlush(any());
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
        verify(seqRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldFormatZeroPaddedSequence_forHighNumbers() {
        int year = 2026;
        CaseNumberSeq existing = new CaseNumberSeq(year);
        existing.setLastSeq(12345);

        when(seqRepository.findByYearForUpdate(year)).thenReturn(Optional.of(existing));

        assertThat(service.generateNext()).isEqualTo("CASE-" + year + "-12346");
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
        verify(seqRepository, never()).saveAndFlush(any());
    }
}