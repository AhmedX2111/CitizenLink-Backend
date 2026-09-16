package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.dto.agent.response.WorkloadIndicatorsResponse;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.exception.ResourceNotFoundException;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.CaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DashboardServiceImpl#getWorkloadIndicators} (US-54):
 * role scoping (HANDLER = PERSONAL with ASSIGNED/OVERDUE/DUE_TODAY,
 * SUPERVISOR = TEAM with OVERDUE/DUE_TODAY/UNASSIGNED), per-indicator
 * counts and deep links, and the unknown-user guard.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock private CaseRepository caseRepository;
    @Mock private AppUserRepository userRepository;

    private DashboardServiceImpl dashboardService;

    private UUID userId;
    private AppUser handler;
    private AppUser supervisor;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardServiceImpl(
                caseRepository, userRepository, "Asia/Riyadh");

        userId = UUID.randomUUID();
        handler = user(UserRole.HANDLER);
        supervisor = user(UserRole.SUPERVISOR);
    }

    private AppUser user(UserRole role) {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setUsername("u-" + role);
        u.setDisplayName("User " + role);
        u.setRole(role);
        u.setActive(true);
        return u;
    }

    @Nested
    class HandlerScope {

        @Test
        void returnsPersonalScopeWithAssignedOverdueAndDueToday() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(handler));
            // countInbox is called in order: assigned (all), overdue, dueToday
            when(caseRepository.count(ArgumentMatchers.<Specification<Case>>any())).thenReturn(12L, 4L, 2L);

            WorkloadIndicatorsResponse response = dashboardService.getWorkloadIndicators(userId);

            assertThat(response.scope()).isEqualTo(WorkloadIndicatorsResponse.Scope.PERSONAL);
            assertThat(response.indicators()).hasSize(3);

            WorkloadIndicatorsResponse.Indicator assigned = response.indicators().get(0);
            assertThat(assigned.key()).isEqualTo(WorkloadIndicatorsResponse.Key.ASSIGNED);
            assertThat(assigned.count()).isEqualTo(12L);
            assertThat(assigned.link()).isEqualTo("/api/v1/dashboard/my-inbox");

            WorkloadIndicatorsResponse.Indicator overdue = response.indicators().get(1);
            assertThat(overdue.key()).isEqualTo(WorkloadIndicatorsResponse.Key.OVERDUE);
            assertThat(overdue.count()).isEqualTo(4L);
            assertThat(overdue.link()).isEqualTo("/api/v1/dashboard/my-inbox?overdue=true");

            WorkloadIndicatorsResponse.Indicator dueToday = response.indicators().get(2);
            assertThat(dueToday.key()).isEqualTo(WorkloadIndicatorsResponse.Key.DUE_TODAY);
            assertThat(dueToday.count()).isEqualTo(2L);
            assertThat(dueToday.link()).isEqualTo("/api/v1/dashboard/my-inbox?dueToday=true");
        }
    }

    @Nested
    class SupervisorScope {

        @Test
        void returnsTeamScopeWithOverdueDueTodayAndUnassigned() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(supervisor));
            // countCaseList is called in order: overdue, dueToday, unassigned
            when(caseRepository.count(ArgumentMatchers.<Specification<Case>>any())).thenReturn(5L, 3L, 1L);

            WorkloadIndicatorsResponse response = dashboardService.getWorkloadIndicators(userId);

            assertThat(response.scope()).isEqualTo(WorkloadIndicatorsResponse.Scope.TEAM);
            assertThat(response.indicators()).hasSize(3);

            WorkloadIndicatorsResponse.Indicator overdue = response.indicators().get(0);
            assertThat(overdue.key()).isEqualTo(WorkloadIndicatorsResponse.Key.OVERDUE);
            assertThat(overdue.count()).isEqualTo(5L);
            assertThat(overdue.link()).isEqualTo("/api/v1/cases?overdue=true");

            WorkloadIndicatorsResponse.Indicator dueToday = response.indicators().get(1);
            assertThat(dueToday.key()).isEqualTo(WorkloadIndicatorsResponse.Key.DUE_TODAY);
            assertThat(dueToday.count()).isEqualTo(3L);
            assertThat(dueToday.link()).isEqualTo("/api/v1/cases?dueToday=true");

            WorkloadIndicatorsResponse.Indicator unassigned = response.indicators().get(2);
            assertThat(unassigned.key()).isEqualTo(WorkloadIndicatorsResponse.Key.UNASSIGNED);
            assertThat(unassigned.count()).isEqualTo(1L);
            assertThat(unassigned.link()).isEqualTo("/api/v1/cases?unassigned=true");
        }
    }

    @Nested
    class AdminScope {

        @Test
        void returnsTeamScopeWithOverdueDueTodayAndUnassigned() {
            AppUser admin = user(UserRole.ADMIN);
            when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
            when(caseRepository.count(ArgumentMatchers.<Specification<Case>>any())).thenReturn(8L, 2L, 3L);

            WorkloadIndicatorsResponse response = dashboardService.getWorkloadIndicators(userId);

            assertThat(response.scope()).isEqualTo(WorkloadIndicatorsResponse.Scope.TEAM);
            assertThat(response.indicators()).hasSize(3);

            assertThat(response.indicators().get(0).key()).isEqualTo(WorkloadIndicatorsResponse.Key.OVERDUE);
            assertThat(response.indicators().get(0).count()).isEqualTo(8L);
            assertThat(response.indicators().get(1).key()).isEqualTo(WorkloadIndicatorsResponse.Key.DUE_TODAY);
            assertThat(response.indicators().get(1).count()).isEqualTo(2L);
            assertThat(response.indicators().get(2).key()).isEqualTo(WorkloadIndicatorsResponse.Key.UNASSIGNED);
            assertThat(response.indicators().get(2).count()).isEqualTo(3L);
        }
    }

    @Nested
    class Guards {

        @Test
        void unknownUser_throwsNotFound() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dashboardService.getWorkloadIndicators(userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
