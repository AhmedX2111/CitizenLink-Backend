package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.entities.CaseNumberSeq;
import com.ntg.CitizenLink.repositories.CaseNumberSeqRepository;
import com.ntg.CitizenLink.service.interfaces.CaseNumberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseNumberServiceImpl implements CaseNumberService {

    private final CaseNumberSeqRepository seqRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext() {
        int year = Year.now().getValue();

        CaseNumberSeq seq = seqRepository
                .findByYearForUpdate(year)
                .orElseGet(() -> seqRepository.saveAndFlush(new CaseNumberSeq(year)));

        seqRepository.incrementSequence(year);
        seqRepository.flush();

        int nextSeq = seqRepository.findByYearForUpdate(year)
                .orElseThrow()
                .getLastSeq();

        String caseNumber = String.format("CASE-%d-%05d", year, nextSeq);
        log.debug("Generated case number: {}", caseNumber);

        return caseNumber;
    }
}
