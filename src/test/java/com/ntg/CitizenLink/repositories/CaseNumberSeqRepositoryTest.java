package com.ntg.CitizenLink.repositories;

import com.ntg.CitizenLink.entities.CaseNumberSeq;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CaseNumberSeqRepositoryTest {

    @Autowired private CaseNumberSeqRepository seqRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void saveNewYear_defaultsLastSeqToZero() {
        CaseNumberSeq saved = seqRepository.save(new CaseNumberSeq(2026));
        assertThat(saved.getLastSeq()).isZero();
    }

    @Test
    void findByYearForUpdate_returnsEmpty_whenYearMissing() {
        assertThat(seqRepository.findByYearForUpdate(1999)).isEmpty();
    }

    @Test
    void findByYearForUpdate_returnsLatestValue_afterPessimisticRead() {
        CaseNumberSeq seq = new CaseNumberSeq(2027);
        seq.setLastSeq(41);
        seqRepository.save(seq);

        CaseNumberSeq locked = seqRepository.findByYearForUpdate(2027).orElseThrow();
        locked.setLastSeq(locked.getLastSeq() + 1);

        CaseNumberSeq reloaded = reload(2027);
        assertThat(reloaded.getLastSeq()).isEqualTo(42);
    }

    private CaseNumberSeq reload(int year) {
        entityManager.flush();
        entityManager.clear();
        return seqRepository.findByYearForUpdate(year).orElseThrow();
    }
}