package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.entities.CaseNumberSeq;
import com.ntg.citizenlink.repositories.CaseNumberSeqRepository;
import com.ntg.citizenlink.service.interfaces.CaseNumberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneId;

@Slf4j
@Service
public class CaseNumberServiceImpl implements CaseNumberService {

    private final CaseNumberSeqRepository seqRepository;
    private final ZoneId zoneId;

    public CaseNumberServiceImpl(CaseNumberSeqRepository seqRepository,
                                 @Value("${app.time-zone:}") String timeZone) {
        this.seqRepository = seqRepository;
        this.zoneId = (timeZone == null || timeZone.isBlank()) ? ZoneId.systemDefault() : ZoneId.of(timeZone);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext() {
        int year = Year.now(zoneId).getValue();

        // Seed the per-year row if this is the first case of the year. The
        // native INSERT ... ON CONFLICT (year) DO NOTHING is atomic and
        // conflict-tolerant, so concurrent requests at year rollover cannot
        // race on the primary key the way saveAndFlush() could.
        seqRepository.insertIfAbsent(year);

        CaseNumberSeq seq = seqRepository
                .findByYearForUpdate(year)
                .orElseThrow(() -> new IllegalStateException(
                        "case_number_seq row missing for year " + year + " after upsert"));

        seq.setLastSeq(seq.getLastSeq() + 1);

        String caseNumber = String.format("CASE-%d-%05d", year, seq.getLastSeq());
        log.debug("Generated case number: {}", caseNumber);

        return caseNumber;
    }
}
