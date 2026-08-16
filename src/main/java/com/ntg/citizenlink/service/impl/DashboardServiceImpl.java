package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.dto.agent.response.DashboardSummaryResponse;
import com.ntg.citizenlink.dto.agent.response.MyOpenCaseResponse;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.repositories.CaseRepository;
import com.ntg.citizenlink.service.interfaces.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final CaseRepository caseRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        LocalDate today = now.toLocalDate();
        OffsetDateTime monthStart = today.withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime monthEnd = monthStart.plusMonths(1);

        OffsetDateTime dayStart = today.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime dayEnd = dayStart.plusDays(1);

        long openCases = caseRepository.countOpenCases();
        long resolvedThisMonth = caseRepository.countResolvedBetween(monthStart, monthEnd);
        long overdueCases = caseRepository.countOverdueCases(now);
        long newToday = caseRepository.countCreatedBetween(dayStart, dayEnd);

        DashboardSummaryResponse.KpiSummary kpis = new DashboardSummaryResponse.KpiSummary(
                openCases, resolvedThisMonth, overdueCases, newToday
        );

        Map<String, Long> statusCounts = buildStatusCounts();

        return new DashboardSummaryResponse(kpis, statusCounts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyOpenCaseResponse> getMyOpenCases(UUID userId) {
        Pageable top5 = PageRequest.of(0, 5);
        return caseRepository.findTop5OpenCasesByAssignedUser(userId, top5);
    }

    private Map<String, Long> buildStatusCounts() {
        Map<String, Long> result = new LinkedHashMap<>();

        for (CaseStatus status : CaseStatus.values()) {
            result.put(status.name(), 0L);
        }

        List<Object[]> rows = caseRepository.countGroupedByStatus();
        for (Object[] row : rows) {
            CaseStatus status = (CaseStatus) row[0];
            Long count = (Long) row[1];
            result.put(status.name(), count);
        }

        return result;
    }
}
