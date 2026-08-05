package com.ntg.CitizenLink.repositories;

import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.entities.StatusHistory;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.UserRole;
import com.ntg.CitizenLink.enums.WorkflowAction;
import com.ntg.CitizenLink.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StatusHistoryRepositoryTest {

    @Autowired private StatusHistoryRepository historyRepository;
    @Autowired private CaseRepository caseRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private AppUserRepository appUserRepository;

    private Case savedCase;
    private AppUser user;

    @BeforeEach
    void setUp() {
        user = appUserRepository.save(EntityFactory.appUser(UserRole.AGENT));
        var citizen = citizenRepository.save(EntityFactory.citizen(user));
        var category = categoryRepository.save(EntityFactory.category());
        var department = departmentRepository.save(EntityFactory.department());
        savedCase = caseRepository.save(EntityFactory.aCase(citizen, category, department, user));
    }

    @Test
    void findByCaseIdOrderByCreatedAtAsc_returnsChronologicalHistory() {
        StatusHistory first = EntityFactory.statusHistory(
                savedCase, user, null, CaseStatus.NEW, WorkflowAction.CREATE);
        first.setCreatedAt(OffsetDateTime.now().minusSeconds(10));
        StatusHistory second = EntityFactory.statusHistory(
                savedCase, user, CaseStatus.NEW, CaseStatus.ASSIGNED, WorkflowAction.ASSIGN);
        second.setCreatedAt(OffsetDateTime.now());

        historyRepository.save(first);
        historyRepository.save(second);

        List<StatusHistory> result =
                historyRepository.findByCaseIdOrderByCreatedAtAsc(savedCase.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAction()).isEqualTo(WorkflowAction.CREATE);
        assertThat(result.get(1).getAction()).isEqualTo(WorkflowAction.ASSIGN);
    }

    @Test
    void findByCaseIdOrderByCreatedAtAsc_returnsOnlyThatCasesHistory() {
        historyRepository.save(EntityFactory.statusHistory(
                savedCase, user, null, CaseStatus.NEW, WorkflowAction.CREATE));

        Case otherCase = caseRepository.save(EntityFactory.aCase(
                savedCase.getCitizen(), savedCase.getCategory(), savedCase.getDepartment(), user));
        historyRepository.save(EntityFactory.statusHistory(
                otherCase, user, null, CaseStatus.NEW, WorkflowAction.CREATE));

        List<StatusHistory> result =
                historyRepository.findByCaseIdOrderByCreatedAtAsc(savedCase.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCaseEntity().getId()).isEqualTo(savedCase.getId());
    }

    @Test
    void findByCaseIdOrderByCreatedAtAsc_returnsEmpty_whenNoHistory() {
        assertThat(historyRepository.findByCaseIdOrderByCreatedAtAsc(savedCase.getId())).isEmpty();
    }
}
