package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.dto.agent.response.DashboardSummaryResponse;
import com.ntg.citizenlink.dto.agent.response.InboxCaseResponse;
import com.ntg.citizenlink.dto.agent.response.InboxCountsResponse;
import com.ntg.citizenlink.dto.agent.response.MyOpenCaseResponse;
import com.ntg.citizenlink.dto.agent.response.PagedResponse;
import com.ntg.citizenlink.dto.agent.response.WorkloadIndicatorsResponse;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.InboxSort;
import com.ntg.citizenlink.enums.Priority;
import com.ntg.citizenlink.exception.ResourceNotFoundException;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.CaseRepository;
import com.ntg.citizenlink.repositories.InboxSpecification;
import com.ntg.citizenlink.service.interfaces.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

    private final CaseRepository caseRepository;
    private final AppUserRepository userRepository;

    /**
     * US-50: "today" for the due-today quick filter is resolved in this zone —
     * the same app.time-zone mechanism CaseNumberServiceImpl uses for the
     * case-number year, so the inbox and the case numbering agree on what a
     * calendar day is. Blank/unset falls back to the JVM default zone.
     */
    private final ZoneId zoneId;

    public DashboardServiceImpl(CaseRepository caseRepository,
                                AppUserRepository userRepository,
                                @Value("${app.time-zone:}") String timeZone) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.zoneId = (timeZone == null || timeZone.isBlank()) ? ZoneId.systemDefault() : ZoneId.of(timeZone);
    }

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

    /**
     * US-49: paged, filtered work inbox for one HANDLER.
     *
     * The role restriction itself lives at the controller (@PreAuthorize
     * hasRole('HANDLER')); here the requester is simply the inbox owner —
     * the InboxSpecification always constrains assignedToUser = userId, so a
     * handler can never see anyone else's workload through this path.
     *
     * Query approach: a dynamic Specification instead of a JPQL query with
     * nullable parameters — PostgreSQL cannot infer the type of a NULL enum
     * bind parameter in (:status IS NULL OR ...) predicates. Goes through the
     * CaseRepository.findAll(Specification, Pageable) override whose entity
     * graph fetches citizen (and the other to-one associations) in the same
     * query, so mapping citizenFullName per row triggers no extra selects.
     *
     * US-51: ordering is applied INSIDE the specification as CriteriaBuilder
     * order items (CASE expressions for the overdue-first flag and the
     * priority rank — Spring Data Sort cannot express those), so the Pageable
     * carries no Sort: passing one would overwrite the specification's order
     * items. The sort enum defaults to SMART (overdue first, then priority
     * rank, then nearest due date) — the default ranking under US-51.
     */
    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InboxCaseResponse> getMyInbox(UUID userId, CaseStatus status,
                                                       Priority priority, String keyword,
                                                       Boolean overdue, Boolean dueToday,
                                                       InboxSort sort,
                                                       int page, int size) {
        log.debug("Building work inbox for handler {}: page={}, size={}, status={}, priority={}, keyword={}, overdue={}, dueToday={}, sort={}",
                userId, page, size, status, priority, keyword, overdue, dueToday, sort);

        OffsetDateTime now = OffsetDateTime.now(zoneId);
        OffsetDateTime todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toOffsetDateTime();
        OffsetDateTime todayEnd = todayStart.plusDays(1);

        InboxSpecification spec = InboxSpecification.forHandler(userId)
                .status(status)
                .priority(priority)
                .keyword(keyword)
                .overdue(overdue, now)
                .dueToday(dueToday, todayStart, todayEnd)
                .sort(sort == null ? InboxSort.SMART : sort)
                .build();

        Pageable pageable = PageRequest.of(page, size);

        Page<Case> inboxPage = caseRepository.findAll(spec, pageable);

        List<InboxCaseResponse> content = inboxPage.getContent().stream()
                .map(this::toInboxResponse)
                .toList();

        return new PagedResponse<>(
                content,
                inboxPage.getNumber(),
                inboxPage.getSize(),
                inboxPage.getTotalElements(),
                inboxPage.getTotalPages()
        );
    }

    /**
     * US-50: quick-filter badge counts for one HANDLER's inbox. Each count is
     * the same base specification (assignedToUser = userId, default
     * final-state exclusion) plus one dimension predicate, so the numbers are
     * exactly what the corresponding quick filter on /my-inbox returns.
     * Six small count queries over the assigned_to_user_id index — simple,
     * correct, and cheap at inbox scale.
     */
    @Override
    @Transactional(readOnly = true)
    public InboxCountsResponse getMyInboxCounts(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(zoneId);
        OffsetDateTime todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toOffsetDateTime();
        OffsetDateTime todayEnd = todayStart.plusDays(1);

        return new InboxCountsResponse(
                countInbox(userId, null, null, null, null, now, todayStart, todayEnd),
                countInbox(userId, true, null, null, null, now, todayStart, todayEnd),
                countInbox(userId, null, true, null, null, now, todayStart, todayEnd),
                countInbox(userId, null, null, Priority.URGENT, null, now, todayStart, todayEnd),
                countInbox(userId, null, null, null, CaseStatus.AWAITING_INFO, now, todayStart, todayEnd),
                countInbox(userId, null, null, null, CaseStatus.ASSIGNED, now, todayStart, todayEnd)
        );
    }

    /**
     * US-50: one count dimension — base inbox conditions (owner + default
     * final-state exclusion) plus at most one dimension predicate.
     */
    private long countInbox(UUID userId, Boolean overdue, Boolean dueToday,
                            Priority priority, CaseStatus status,
                            OffsetDateTime now, OffsetDateTime todayStart, OffsetDateTime todayEnd) {
        return caseRepository.count(InboxSpecification.forHandler(userId)
                .status(status)
                .priority(priority)
                .overdue(overdue, now)
                .dueToday(dueToday, todayStart, todayEnd)
                .build());
    }

    /**
     * US-54 (DSH-04, DSH-05): role-aware workload indicators.
     *
     * HANDLER — PERSONAL scope. Each count is the same base inbox
     * specification used by getMyInboxCounts (assignedToUser = caller,
     * final states excluded) plus one dimension, so every number is exactly
     * what the linked /my-inbox quick filter returns:
     *   ASSIGNED  = all        → /api/v1/dashboard/my-inbox
     *   OVERDUE   = overdue    → /api/v1/dashboard/my-inbox?overdue=true
     *   DUE_TODAY = dueToday   → /api/v1/dashboard/my-inbox?dueToday=true
     *
     * SUPERVISOR — TEAM scope. Counts run over the whole queue (the
     * supervisor's own visibility already covers every case) with the same
     * final-state exclusion, via the US-54 case-list quick filters:
     *   OVERDUE    → /api/v1/cases?overdue=true
     *   DUE_TODAY  → /api/v1/cases?dueToday=true
     *   UNASSIGNED → /api/v1/cases?unassigned=true
     * An ASSIGNED indicator makes no sense team-wide (it equals openCases on
     * the summary KPI card), so supervisors get UNASSIGNED instead — the
     * pressure signal the user story asks for.
     *
     * All counts go through countCaseList, which applies the identical
     * CaseSpecification predicates the /api/v1/cases list uses — count and
     * linked list can never diverge.
     */
    @Override
    @Transactional(readOnly = true)
    public WorkloadIndicatorsResponse getWorkloadIndicators(UUID userId) {
        AppUser requester = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", userId));

        OffsetDateTime now = OffsetDateTime.now(zoneId);
        OffsetDateTime todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toOffsetDateTime();
        OffsetDateTime todayEnd = todayStart.plusDays(1);

        List<WorkloadIndicatorsResponse.Indicator> indicators = new ArrayList<>();

        switch (requester.getRole()) {
            case HANDLER -> {
                indicators.add(new WorkloadIndicatorsResponse.Indicator(
                        WorkloadIndicatorsResponse.Key.ASSIGNED,
                        countInbox(userId, null, null, null, null, now, todayStart, todayEnd),
                        "/api/v1/dashboard/my-inbox"));
                indicators.add(new WorkloadIndicatorsResponse.Indicator(
                        WorkloadIndicatorsResponse.Key.OVERDUE,
                        countInbox(userId, true, null, null, null, now, todayStart, todayEnd),
                        "/api/v1/dashboard/my-inbox?overdue=true"));
                indicators.add(new WorkloadIndicatorsResponse.Indicator(
                        WorkloadIndicatorsResponse.Key.DUE_TODAY,
                        countInbox(userId, null, true, null, null, now, todayStart, todayEnd),
                        "/api/v1/dashboard/my-inbox?dueToday=true"));
            }
            case SUPERVISOR, ADMIN -> {
                indicators.add(new WorkloadIndicatorsResponse.Indicator(
                        WorkloadIndicatorsResponse.Key.OVERDUE,
                        countCaseList(true, null, null, now, todayStart, todayEnd),
                        "/api/v1/cases?overdue=true"));
                indicators.add(new WorkloadIndicatorsResponse.Indicator(
                        WorkloadIndicatorsResponse.Key.DUE_TODAY,
                        countCaseList(null, true, null, now, todayStart, todayEnd),
                        "/api/v1/cases?dueToday=true"));
                indicators.add(new WorkloadIndicatorsResponse.Indicator(
                        WorkloadIndicatorsResponse.Key.UNASSIGNED,
                        countCaseList(null, null, true, now, todayStart, todayEnd),
                        "/api/v1/cases?unassigned=true"));
            }
            default -> throw new IllegalStateException(
                    "Unexpected role for workload indicators: " + requester.getRole());
        }

        WorkloadIndicatorsResponse.Scope scope =
                requester.getRole() == com.ntg.citizenlink.enums.UserRole.HANDLER
                        ? WorkloadIndicatorsResponse.Scope.PERSONAL
                        : WorkloadIndicatorsResponse.Scope.TEAM;

        return new WorkloadIndicatorsResponse(scope, indicators);
    }

    /**
     * US-54: team-scope count for one workload dimension, evaluated with the
     * exact CaseSpecification the /api/v1/cases list uses (supervisor
     * visibility = no restriction) so the count always matches the linked
     * filtered list.
     */
    private long countCaseList(Boolean overdue, Boolean dueToday, Boolean unassigned,
                               OffsetDateTime now, OffsetDateTime todayStart, OffsetDateTime todayEnd) {
        com.ntg.citizenlink.dto.agent.request.CaseSearchRequest filter =
                new com.ntg.citizenlink.dto.agent.request.CaseSearchRequest();
        filter.setOverdue(overdue);
        filter.setDueToday(dueToday);
        filter.setUnassigned(unassigned);
        return caseRepository.count(new com.ntg.citizenlink.repositories.CaseSpecification(
                filter, null, null, now, todayStart, todayEnd));
    }

    /**
     * Maps a Case row to the compact inbox projection. The citizen full name
     * comes from Citizen.fullName — the same source CaseMapper uses for
     * citizenFullName in the full CaseResponse mapping.
     */
    private InboxCaseResponse toInboxResponse(Case c) {
        return new InboxCaseResponse(
                c.getId(),
                c.getCaseNumber(),
                c.getSubject(),
                c.getCitizen() != null ? c.getCitizen().getFullName() : null,
                c.getPriority(),
                c.getStatus(),
                c.getDueAt(),
                c.getUpdatedAt()
        );
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
