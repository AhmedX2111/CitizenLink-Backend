package com.ntg.citizenlink.service;

import com.ntg.citizenlink.dto.agent.response.VolumeReportResponse;
import com.ntg.citizenlink.repositories.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * US-27: builds the volume report for the given date range.
     * Merges created and resolved counts into a day-by-day table,
     * filling zero for days where either series has no data.
     */
    @Transactional(readOnly = true)
    public VolumeReportResponse getVolumeReport(LocalDate from, LocalDate to) {
        log.info("Building volume report from {} to {}", from, to);

        OffsetDateTime fromDt = from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime toDt   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();

        // ── Daily created ──────────────────────────────────────────
        Map<String, Long> createdMap = new LinkedHashMap<>();
        for (Object[] row : reportRepository.countCreatedPerDay(fromDt, toDt)) {
            createdMap.put(row[0].toString(), (Long) row[1]);
        }

        // ── Daily resolved ─────────────────────────────────────────
        Map<String, Long> resolvedMap = new LinkedHashMap<>();
        for (Object[] row : reportRepository.countResolvedPerDay(fromDt, toDt)) {
            resolvedMap.put(row[0].toString(), (Long) row[1]);
        }

        // ── Merge: every day in the range, zero-fill if absent ─────
        List<VolumeReportResponse.DailyVolumeRow> rows = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            String key = cursor.format(DATE_FMT);
            rows.add(VolumeReportResponse.DailyVolumeRow.builder()
                    .date(key)
                    .created(createdMap.getOrDefault(key, 0L))
                    .resolved(resolvedMap.getOrDefault(key, 0L))
                    .build());
            cursor = cursor.plusDays(1);
        }

        // ── Top 5 categories ──────────────────────────────────────
        List<Object[]> catRows = reportRepository.countTopCategories(
                fromDt, toDt, PageRequest.of(0, 5));

        long totalCases = rows.stream().mapToLong(r -> r.getCreated()).sum();

        List<VolumeReportResponse.CategoryCount> categories = new ArrayList<>();
        for (Object[] row : catRows) {
            long count = (Long) row[2];
            double pct = totalCases == 0 ? 0.0
                    : Math.round((count * 1000.0) / totalCases) / 10.0;
            categories.add(VolumeReportResponse.CategoryCount.builder()
                    .categoryNameEn((String) row[0])
                    .categoryNameAr((String) row[1])
                    .count(count)
                    .percentage(pct)
                    .build());
        }

        return VolumeReportResponse.builder()
                .dailyVolume(rows)
                .topCategories(categories)
                .build();
    }
}