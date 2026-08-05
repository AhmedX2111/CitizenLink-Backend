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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CaseNumberServiceImpl} — the per-year sequence
 * generator behind human-readable case numbers (CASE-YYYY-NNNNN).
 *
 * Covers: first-case-of-the-year (creates a fresh sequence row), existing
 * year (reuses the row), correct zero-padded formatting, and the defensive
 * failure path when the sequence row disappears between the UPDATE and the
 * read-back.
 */
@ExtendWith(MockitoExtension.class)
class CaseNumberServiceImplTest {

    @Mock private CaseNumberSeqRepository seqRepository;

    @InjectMocks private CaseNumberServiceImpl service;

    @Test
    void shouldCreateNewSequenceRow_andFormatCaseNumber_whenYearDoesNotExist() {
        int year = 2026;
        CaseNumberSeq created = new CaseNumberSeq(year);
        created.setLastSeq(0);
        CaseNumberSeq afterIncrement = new CaseNumberSeq(year);
        afterIncrement.setLastSeq(1);

        // call 1 (initial lookup) -> empty, triggers saveAndFlush
        // call 2 (read-back after increment) -> lastSeq = 1
        when(seqRepository.findByYearForUpdate(year))
                .thenReturn(Optional.empty(), Optional.of(afterIncrement));
        when(seqRepository.saveAndFlush(any())).thenReturn(created);

        String caseNumber = service.generateNext();

        assertThat(caseNumber).isEqualTo("CASE-" + year + "-00001");
        verify(seqRepository).incrementSequence(year);
        verify(seqRepository).saveAndFlush(any());
    }

    @Test
    void shouldReuseExistingSequenceRow_whenYearExists() {
        int year = 2026;
        CaseNumberSeq existing = new CaseNumberSeq(year);
        existing.setLastSeq(10);
        CaseNumberSeq afterIncrement = new CaseNumberSeq(year);
        afterIncrement.setLastSeq(11);

        when(seqRepository.findByYearForUpdate(year))
                .thenReturn(Optional.of(existing), Optional.of(afterIncrement));

        String caseNumber = service.generateNext();

        assertThat(caseNumber).isEqualTo("CASE-" + year + "-00011");
        verify(seqRepository).incrementSequence(year);
    }

    @Test
    void shouldFormatZeroPaddedSequence_forHighNumbers() {
        int year = 2026;
        CaseNumberSeq existing = new CaseNumberSeq(year);
        existing.setLastSeq(12345);
        CaseNumberSeq afterIncrement = new CaseNumberSeq(year);
        afterIncrement.setLastSeq(12346);

        when(seqRepository.findByYearForUpdate(year))
                .thenReturn(Optional.of(existing), Optional.of(afterIncrement));

        assertThat(service.generateNext()).isEqualTo("CASE-" + year + "-12346");
    }

    @Test
    void shouldThrowIllegalState_whenSequenceRowDisappearsAfterIncrement() {
        int year = 2026;
        CaseNumberSeq existing = new CaseNumberSeq(year);

        // call 1 returns the existing row; call 2 (read-back) is empty
        when(seqRepository.findByYearForUpdate(year))
                .thenReturn(Optional.of(existing), Optional.empty());

        assertThatThrownBy(() -> service.generateNext())
                .isInstanceOf(IllegalStateException.class);
    }
}
