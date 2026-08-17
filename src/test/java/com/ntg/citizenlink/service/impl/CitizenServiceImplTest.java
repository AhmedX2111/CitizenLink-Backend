package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.dto.agent.response.CitizenProfileResponse;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.Priority;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.exception.ResourceNotFoundException;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.CaseRepository;
import com.ntg.citizenlink.repositories.CitizenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CitizenServiceImpl#getCitizenProfile}.
 *
 * Covers the M-17 role-to-visibility-filter mapping (ADMIN/SUPERVISOR see all,
 * HANDLER restricted to assigned cases, AGENT restricted to created cases),
 * the total/open/resolved derivation from the single grouped count query,
 * the bounded PageRequest.of(0, 5) recent-cases fetch, and the 404 guards.
 */
@ExtendWith(MockitoExtension.class)
class CitizenServiceImplTest {

    @Mock private CitizenRepository citizenRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private CaseRepository caseRepository;

    @InjectMocks private CitizenServiceImpl citizenService;

    private UUID citizenId;
    private UUID requesterId;
    private Citizen citizen;
    private AppUser requester;

    @BeforeEach
    void setUp() {
        citizenId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        citizen = new Citizen();
        citizen.setId(citizenId);
        citizen.setFullName("Citizen One");
        requester = new AppUser();
        requester.setId(requesterId);
        requester.setDisplayName("User");
        requester.setRole(UserRole.ADMIN);
    }

    private void stubLookups() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        when(appUserRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    }

    private Object[] statusRow(CaseStatus status, long count) {
        return new Object[]{status, count};
    }

    private Case aCase(CaseStatus status) {
        Case c = new Case();
        c.setId(UUID.randomUUID());
        c.setCaseNumber("CASE-" + status);
        c.setSubject("Subject " + status);
        c.setStatus(status);
        c.setPriority(Priority.MEDIUM);
        c.setCreatedAt(OffsetDateTime.now());
        return c;
    }

    @Test
    void getCitizenProfile_adminPassesNoVisibilityFilters() {
        stubLookups();
        when(caseRepository.countVisibleByCitizenIdByStatus(citizenId, null, null))
                .thenReturn(List.<Object[]>of(statusRow(CaseStatus.NEW, 1L)));
        when(caseRepository.findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, null, null, PageRequest.of(0, 5)))
                .thenReturn(List.of(aCase(CaseStatus.NEW)));

        CitizenProfileResponse response = citizenService.getCitizenProfile(citizenId, requesterId);

        assertThat(response.getId()).isEqualTo(citizenId);
        assertThat(response.getFullName()).isEqualTo("Citizen One");
        assertThat(response.getTotalCases()).isEqualTo(1);
        assertThat(response.getOpenCases()).isEqualTo(1);
        assertThat(response.getResolvedCases()).isZero();
        assertThat(response.getRecentCases()).hasSize(1);
    }

    @Test
    void getCitizenProfile_supervisorPassesNoVisibilityFilters() {
        requester.setRole(UserRole.SUPERVISOR);
        stubLookups();
        when(caseRepository.countVisibleByCitizenIdByStatus(citizenId, null, null))
                .thenReturn(List.<Object[]>of());
        when(caseRepository.findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, null, null, PageRequest.of(0, 5)))
                .thenReturn(List.of());

        CitizenProfileResponse response = citizenService.getCitizenProfile(citizenId, requesterId);

        assertThat(response.getTotalCases()).isZero();
        verify(caseRepository).countVisibleByCitizenIdByStatus(citizenId, null, null);
        verify(caseRepository).findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, null, null, PageRequest.of(0, 5));
    }

    @Test
    void getCitizenProfile_handlerRestrictsToAssignedCases() {
        requester.setRole(UserRole.HANDLER);
        stubLookups();
        when(caseRepository.countVisibleByCitizenIdByStatus(citizenId, null, requesterId))
                .thenReturn(List.<Object[]>of());
        when(caseRepository.findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, null, requesterId, PageRequest.of(0, 5)))
                .thenReturn(List.of());

        citizenService.getCitizenProfile(citizenId, requesterId);

        verify(caseRepository).countVisibleByCitizenIdByStatus(citizenId, null, requesterId);
        verify(caseRepository).findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, null, requesterId, PageRequest.of(0, 5));
    }

    @Test
    void getCitizenProfile_agentRestrictsToCreatedCases() {
        requester.setRole(UserRole.AGENT);
        stubLookups();
        when(caseRepository.countVisibleByCitizenIdByStatus(citizenId, requesterId, null))
                .thenReturn(List.<Object[]>of());
        when(caseRepository.findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, requesterId, null, PageRequest.of(0, 5)))
                .thenReturn(List.of());

        citizenService.getCitizenProfile(citizenId, requesterId);

        verify(caseRepository).countVisibleByCitizenIdByStatus(citizenId, requesterId, null);
        verify(caseRepository).findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, requesterId, null, PageRequest.of(0, 5));
    }

    @Test
    void getCitizenProfile_derivesTotalsFromGroupedStatuses() {
        stubLookups();
        when(caseRepository.countVisibleByCitizenIdByStatus(citizenId, null, null))
                .thenReturn(List.of(
                        statusRow(CaseStatus.NEW, 2L),
                        statusRow(CaseStatus.ASSIGNED, 1L),
                        statusRow(CaseStatus.IN_PROGRESS, 1L),
                        statusRow(CaseStatus.RESOLVED, 1L),
                        statusRow(CaseStatus.CLOSED, 1L),
                        statusRow(CaseStatus.CANCELLED, 1L)));
        when(caseRepository.findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, null, null, PageRequest.of(0, 5)))
                .thenReturn(List.of());

        CitizenProfileResponse response = citizenService.getCitizenProfile(citizenId, requesterId);

        assertThat(response.getTotalCases()).isEqualTo(7);
        assertThat(response.getOpenCases()).isEqualTo(4); // NEW + ASSIGNED + IN_PROGRESS
        assertThat(response.getResolvedCases()).isEqualTo(2); // RESOLVED + CLOSED
    }

    @Test
    void getCitizenProfile_recentCasesLimitedToFiveAndMapped() {
        stubLookups();
        when(caseRepository.countVisibleByCitizenIdByStatus(citizenId, null, null))
                .thenReturn(List.<Object[]>of());
        when(caseRepository.findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, null, null, PageRequest.of(0, 5)))
                .thenReturn(List.of(aCase(CaseStatus.NEW), aCase(CaseStatus.ASSIGNED)));

        CitizenProfileResponse response = citizenService.getCitizenProfile(citizenId, requesterId);

        assertThat(response.getRecentCases()).hasSize(2);
        assertThat(response.getRecentCases().get(0).getCaseNumber()).isEqualTo("CASE-NEW");
        verify(caseRepository).findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, null, null, PageRequest.of(0, 5));
    }

    @Test
    void getCitizenProfile_throwsWhenCitizenNotFound() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> citizenService.getCitizenProfile(citizenId, requesterId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCitizenProfile_throwsWhenRequesterNotFound() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        when(appUserRepository.findById(requesterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> citizenService.getCitizenProfile(citizenId, requesterId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
