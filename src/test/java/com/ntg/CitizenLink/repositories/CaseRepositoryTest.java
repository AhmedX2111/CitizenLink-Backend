package com.ntg.CitizenLink.repositories;

import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.entities.Citizen;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.ActiveProfiles("test")
class CaseRepositoryTest {

    @Autowired private CaseRepository caseRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private AppUserRepository appUserRepository;

    private Citizen citizenA;
    private Citizen citizenB;
    private com.ntg.CitizenLink.entities.Category category;
    private com.ntg.CitizenLink.entities.Department department;
    private com.ntg.CitizenLink.entities.AppUser creator;

    @BeforeEach
    void setUp() {
        creator = appUserRepository.save(EntityFactory.appUser(com.ntg.CitizenLink.enums.UserRole.AGENT));
        citizenA = citizenRepository.save(EntityFactory.citizen(creator));
        citizenB = citizenRepository.save(EntityFactory.citizen(creator));
        category = categoryRepository.save(EntityFactory.category());
        department = departmentRepository.save(EntityFactory.department());
    }

    private Case savedCase(Citizen citizen, CaseStatus status) {
        Case c = EntityFactory.aCase(citizen, category, department, creator);
        c.setStatus(status);
        return caseRepository.save(c);
    }

    @Test
    void existsByCaseNumber_returnsTrue_forDuplicate() {
        Case c = savedCase(citizenA, CaseStatus.NEW);
        assertThat(caseRepository.existsByCaseNumber(c.getCaseNumber())).isTrue();
    }

    @Test
    void existsByCaseNumber_returnsFalse_whenMissing() {
        assertThat(caseRepository.existsByCaseNumber("CASE-9999-00000")).isFalse();
    }

    @Test
    void countByCitizenId_returnsTotalCasesForCitizen() {
        savedCase(citizenA, CaseStatus.NEW);
        savedCase(citizenA, CaseStatus.RESOLVED);
        savedCase(citizenB, CaseStatus.NEW);

        assertThat(caseRepository.countByCitizenId(citizenA.getId())).isEqualTo(2);
        assertThat(caseRepository.countByCitizenId(citizenB.getId())).isEqualTo(1);
    }

    @Test
    void countOpenCases_countsOnlyNewAssignedInProgress() {
        savedCase(citizenA, CaseStatus.NEW);
        savedCase(citizenA, CaseStatus.IN_PROGRESS);
        savedCase(citizenB, CaseStatus.RESOLVED);
        savedCase(citizenB, CaseStatus.CANCELLED);

        assertThat(caseRepository.countOpenCases()).isEqualTo(2);
    }

    @Test
    void countResolvedBetween_countsOnlyResolvedInRange() {
        OffsetDateTime now = OffsetDateTime.now();

        Case inRange = savedCase(citizenA, CaseStatus.RESOLVED);
        inRange.setResolvedAt(now);
        caseRepository.save(inRange);

        Case outOfRange = savedCase(citizenB, CaseStatus.RESOLVED);
        outOfRange.setResolvedAt(now.minusMonths(3));
        caseRepository.save(outOfRange);

        assertThat(caseRepository.countResolvedBetween(now.minusDays(1), now.plusDays(1)))
                .isEqualTo(1);
    }

    @Test
    void countOverdueCases_countsOnlyOpenOverdue() {
        OffsetDateTime now = OffsetDateTime.now();

        Case overdueOpen = savedCase(citizenA, CaseStatus.NEW);
        overdueOpen.setDueAt(now.minusDays(1));
        caseRepository.save(overdueOpen);

        Case overdueClosed = savedCase(citizenA, CaseStatus.CLOSED);
        overdueClosed.setDueAt(now.minusDays(2));
        caseRepository.save(overdueClosed);

        Case notOverdue = savedCase(citizenB, CaseStatus.NEW);
        notOverdue.setDueAt(now.plusDays(1));
        caseRepository.save(notOverdue);

        assertThat(caseRepository.countOverdueCases(now)).isEqualTo(1);
    }

    @Test
    void countCreatedBetween_countsCasesInRange() {
        OffsetDateTime now = OffsetDateTime.now();
        savedCase(citizenA, CaseStatus.NEW);
        savedCase(citizenB, CaseStatus.ASSIGNED);

        assertThat(caseRepository.countCreatedBetween(now.minusDays(1), now.plusDays(1)))
                .isEqualTo(2);
    }

    @Test
    void countGroupedByStatus_groupsAllStatuses() {
        savedCase(citizenA, CaseStatus.NEW);
        savedCase(citizenB, CaseStatus.NEW);
        savedCase(citizenA, CaseStatus.ASSIGNED);

        Map<CaseStatus, Long> byStatus = caseRepository.countGroupedByStatus().stream()
                .collect(toMap(row -> (CaseStatus) row[0], row -> (Long) row[1]));

        assertThat(byStatus)
                .containsEntry(CaseStatus.NEW, 2L)
                .containsEntry(CaseStatus.ASSIGNED, 1L);
    }

    @Test
    void findByCitizenIdOrderByCreatedAtDesc_returnsOnlyThatCitizensCases() {
        Case c1 = savedCase(citizenA, CaseStatus.NEW);
        Case c2 = savedCase(citizenA, CaseStatus.ASSIGNED);
        savedCase(citizenB, CaseStatus.NEW);

        List<Case> result = caseRepository.findByCitizenIdOrderByCreatedAtDesc(
                citizenA.getId(), PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Case::getId)
                .containsExactlyInAnyOrder(c1.getId(), c2.getId());
    }

    @Test
    void countByCitizenIdAndStatusIn_filtersByStatusList() {
        savedCase(citizenA, CaseStatus.NEW);
        savedCase(citizenA, CaseStatus.RESOLVED);

        assertThat(caseRepository.countByCitizenIdAndStatusIn(
                citizenA.getId(), List.of(CaseStatus.NEW, CaseStatus.ASSIGNED))).isEqualTo(1);
        assertThat(caseRepository.countByCitizenIdAndStatusNotIn(
                citizenA.getId(), List.of(CaseStatus.NEW))).isEqualTo(1);
    }

    @Test
    void findTop5OpenCasesByAssignedUser_returnsOnlyOpenAndOrdersByDueDate() {
        OffsetDateTime now = OffsetDateTime.now();
        var handler = appUserRepository.save(
                EntityFactory.appUser(com.ntg.CitizenLink.enums.UserRole.HANDLER));

        Case lessUrgent = savedCase(citizenA, CaseStatus.NEW);
        lessUrgent.setAssignedToUser(handler);
        lessUrgent.setDueAt(now.plusDays(3));
        caseRepository.save(lessUrgent);

        Case moreUrgent = savedCase(citizenB, CaseStatus.IN_PROGRESS);
        moreUrgent.setAssignedToUser(handler);
        moreUrgent.setDueAt(now.plusDays(1));
        caseRepository.save(moreUrgent);

        Case closed = savedCase(citizenA, CaseStatus.RESOLVED);
        closed.setAssignedToUser(handler);
        caseRepository.save(closed);

        var result = caseRepository.findTop5OpenCasesByAssignedUser(
                handler.getId(), PageRequest.of(0, 5));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).caseNumber()).isEqualTo(moreUrgent.getCaseNumber());
        assertThat(result.get(1).caseNumber()).isEqualTo(lessUrgent.getCaseNumber());
    }
}
