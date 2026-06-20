package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.dto.agent.response.DashboardSummaryResponse;
import com.ntg.CitizenLink.dto.agent.response.MyOpenCaseResponse;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.repositories.CaseRepository;
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

/**
 * Backs US-04 (KPI cards), US-05 (status chart), and US-06 (My Open Cases).
 *
 * Timezone handling: all date-boundary calculations use ZoneOffset.UTC
 * for consistency. If the deployment requires a specific business timezone
 * (e.g. Africa/Cairo for Egypt government hours), inject a ZoneId bean
 * instead of hardcoding UTC here.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CaseRepository caseRepository;

    /**
     * US-04 + US-05 combined — single endpoint, single DB round-trip set,
     * because both KPIs and the chart are shown on the same dashboard view.
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Month boundaries
        LocalDate today = now.toLocalDate();
        OffsetDateTime monthStart = today.withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime monthEnd = monthStart.plusMonths(1);

        // Day boundaries
        OffsetDateTime dayStart = today.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime dayEnd = dayStart.plusDays(1);

        long openCases         = caseRepository.countOpenCases();
        long resolvedThisMonth = caseRepository.countResolvedBetween(monthStart, monthEnd);
        long overdueCases      = caseRepository.countOverdueCases(now);
        long newToday          = caseRepository.countCreatedBetween(dayStart, dayEnd);

        DashboardSummaryResponse.KpiSummary kpis = new DashboardSummaryResponse.KpiSummary(
                openCases, resolvedThisMonth, overdueCases, newToday
        );

        Map<String, Long> statusCounts = buildStatusCounts();

        return new DashboardSummaryResponse(kpis, statusCounts);
    }

    /**
     * US-05: builds a complete map of every CaseStatus -> count,
     * defaulting to 0 for statuses with no cases yet so the frontend
     * chart always renders all 8 categories consistently.
     */
    private Map<String, Long> buildStatusCounts() {
        Map<String, Long> result = new LinkedHashMap<>();

        // Initialize all statuses to zero first — guarantees stable ordering
        // and full coverage even for statuses with no cases.
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

    /**
     * US-06: top 5 open cases assigned to the given HANDLER, due-date ascending.
     */
    @Transactional(readOnly = true)
    public List<MyOpenCaseResponse> getMyOpenCases(UUID userId) {
        Pageable top5 = PageRequest.of(0, 5);
        return caseRepository.findTop5OpenCasesByAssignedUser(userId, top5);
    }
}