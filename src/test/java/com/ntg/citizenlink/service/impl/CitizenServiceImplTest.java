package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.dto.agent.response.CitizenProfileResponse;
import com.ntg.citizenlink.dto.agent.response.CitizenResponse;
import com.ntg.citizenlink.dto.agent.request.CitizenSearchRequest;
import com.ntg.citizenlink.dto.agent.request.CreateCitizenRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
        citizen.setNationalId("1234567890123456");
        citizen.setPhone("01012345678");
        citizen.setEmail("citizen@test.gov");
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

    /**
     * L-05: getCitizenById must use the same access-filtered case count as
     * getCitizenProfile so the two endpoints never show contradictory totals.
     */
    @Test
    void getCitizenById_adminCountsAllVisibleCases() {
        requester.setRole(UserRole.ADMIN);
        stubLookups();
        when(caseRepository.countVisibleByCitizenId(citizenId, null, null)).thenReturn(3L);

        CitizenResponse response = citizenService.getCitizenById(citizenId, requesterId);

        assertThat(response.getCaseCount()).isEqualTo(3);
        verify(caseRepository).countVisibleByCitizenId(citizenId, null, null);
    }

    @Test
    void getCitizenById_supervisorCountsAllVisibleCases() {
        requester.setRole(UserRole.SUPERVISOR);
        stubLookups();
        when(caseRepository.countVisibleByCitizenId(citizenId, null, null)).thenReturn(2L);

        CitizenResponse response = citizenService.getCitizenById(citizenId, requesterId);

        assertThat(response.getCaseCount()).isEqualTo(2);
    }

    @Test
    void getCitizenById_handlerRestrictsToAssignedCases() {
        requester.setRole(UserRole.HANDLER);
        stubLookups();
        when(caseRepository.countVisibleByCitizenId(citizenId, null, requesterId)).thenReturn(1L);

        CitizenResponse response = citizenService.getCitizenById(citizenId, requesterId);

        assertThat(response.getCaseCount()).isEqualTo(1);
        verify(caseRepository).countVisibleByCitizenId(citizenId, null, requesterId);
    }

    @Test
    void getCitizenById_agentRestrictsToCreatedCases() {
        requester.setRole(UserRole.AGENT);
        stubLookups();
        when(caseRepository.countVisibleByCitizenId(citizenId, requesterId, null)).thenReturn(0L);

        CitizenResponse response = citizenService.getCitizenById(citizenId, requesterId);

        assertThat(response.getCaseCount()).isZero();
        verify(caseRepository).countVisibleByCitizenId(citizenId, requesterId, null);
    }

    @Test
    void getCitizenById_throwsWhenCitizenNotFound() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> citizenService.getCitizenById(citizenId, requesterId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCitizenById_throwsWhenRequesterNotFound() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        when(appUserRepository.findById(requesterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> citizenService.getCitizenById(citizenId, requesterId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── searchCitizens ──────────────────────────────────────────────────

    @Test
    void searchCitizens_emptyRequest_returnsEmptyPage() {
        CitizenSearchRequest request = new CitizenSearchRequest();
        request.setSearchTerm("   ");

        var result = citizenService.searchCitizens(request, requesterId);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void searchCitizens_nullSearchTerm_returnsEmptyPage() {
        CitizenSearchRequest request = new CitizenSearchRequest();
        request.setSearchTerm(null);

        var result = citizenService.searchCitizens(request, requesterId);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void searchCitizens_delegatesToRepositoryWithNormalizedTerms() {
        CitizenSearchRequest request = new CitizenSearchRequest();
        request.setSearchTerm("أحمد");
        request.setPage(0);
        request.setSize(10);

        Citizen citizen = new Citizen();
        citizen.setId(UUID.randomUUID());
        citizen.setFullName("أحمد");
        citizen.setFullNameNormalized("احمد");
        citizen.setNationalId("1234567890123456");
        citizen.setPhone("01012345678");

        Page<Citizen> page = new PageImpl<>(List.of(citizen), PageRequest.of(0, 10), 1);
        when(citizenRepository.searchCitizens("احمد", "أحمد", null, PageRequest.of(0, 10)))
                .thenReturn(page);
        List<Object[]> counts1 = new ArrayList<>();
        counts1.add(new Object[]{citizen.getId(), 5L});
        when(citizenRepository.countCasesByCitizenIds(List.of(citizen.getId())))
                .thenReturn(counts1);
        when(appUserRepository.findById(requesterId)).thenReturn(Optional.of(requester));

        var result = citizenService.searchCitizens(request, requesterId);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFullName()).isEqualTo("أحمد");
        assertThat(result.getContent().get(0).getCaseCount()).isEqualTo(5);
    }

    @Test
    void searchCitizens_phoneSearch_normalizesAndDelegates() {
        CitizenSearchRequest request = new CitizenSearchRequest();
        request.setSearchTerm("+2010 123 456 78");
        request.setPage(0);
        request.setSize(10);

        Citizen citizen = new Citizen();
        citizen.setId(UUID.randomUUID());
        citizen.setFullName("Ahmed");
        citizen.setFullNameNormalized("ahmed");
        citizen.setNationalId("1234567890123456");
        citizen.setPhone("01012345678");

        Page<Citizen> page = new PageImpl<>(List.of(citizen), PageRequest.of(0, 10), 1);
        when(citizenRepository.searchCitizens("+2010 123 456 78", "+2010 123 456 78", "01012345678", PageRequest.of(0, 10)))
                .thenReturn(page);
        List<Object[]> counts2 = new ArrayList<>();
        counts2.add(new Object[]{citizen.getId(), 5L});
        when(citizenRepository.countCasesByCitizenIds(List.of(citizen.getId())))
                .thenReturn(counts2);
        when(appUserRepository.findById(requesterId)).thenReturn(Optional.of(requester));

        var result = citizenService.searchCitizens(request, requesterId);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCaseCount()).isEqualTo(5);
    }

    @Test
    void searchCitizens_invalidPhoneFallsBackToNameAndNationalId() {
        CitizenSearchRequest request = new CitizenSearchRequest();
        request.setSearchTerm("invalid-phone");
        request.setPage(0);
        request.setSize(10);

        Page<Citizen> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(citizenRepository.searchCitizens("invalid-phone", "invalid-phone", null, PageRequest.of(0, 10)))
                .thenReturn(page);

        var result = citizenService.searchCitizens(request, requesterId);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void searchCitizens_arabicNameIsNormalized() {
        CitizenSearchRequest request = new CitizenSearchRequest();
        request.setSearchTerm("أحمد");
        request.setPage(0);
        request.setSize(10);

        Page<Citizen> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(citizenRepository.searchCitizens("احمد", "أحمد", null, PageRequest.of(0, 10)))
                .thenReturn(page);

        citizenService.searchCitizens(request, requesterId);

        verify(citizenRepository).searchCitizens("احمد", "أحمد", null, PageRequest.of(0, 10));
    }

    @Test
    void searchCitizens_batchesCaseCountsInOneQuery() {
        CitizenSearchRequest request = new CitizenSearchRequest();
        request.setSearchTerm("test");
        request.setPage(0);
        request.setSize(10);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Citizen c1 = new Citizen();
        c1.setId(id1);
        c1.setFullName("Test One");
        c1.setFullNameNormalized("test one");
        c1.setNationalId("1111111111111111");
        c1.setPhone("01011111111");
        Citizen c2 = new Citizen();
        c2.setId(id2);
        c2.setFullName("Test Two");
        c2.setFullNameNormalized("test two");
        c2.setNationalId("2222222222222222");
        c2.setPhone("01022222222");

        Page<Citizen> page = new PageImpl<>(List.of(c1, c2), PageRequest.of(0, 10), 2);
        when(citizenRepository.searchCitizens("test", "test", null, PageRequest.of(0, 10)))
                .thenReturn(page);
        List<Object[]> counts3 = new ArrayList<>();
        counts3.add(new Object[]{id1, 3L});
        counts3.add(new Object[]{id2, 1L});
        when(citizenRepository.countCasesByCitizenIds(List.of(id1, id2)))
                .thenReturn(counts3);
        when(appUserRepository.findById(requesterId)).thenReturn(Optional.of(requester));

        var result = citizenService.searchCitizens(request, requesterId);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getCaseCount()).isEqualTo(3);
        assertThat(result.getContent().get(1).getCaseCount()).isEqualTo(1);
    }

    // ── createCitizen normalization ────────────────────────────────────

    @Test
    void createCitizen_normalizesFullNameAndPhone() {
        AppUser createdBy = new AppUser();
        createdBy.setId(UUID.randomUUID());
        createdBy.setUsername("creator");
        when(appUserRepository.findById(requesterId)).thenReturn(Optional.of(createdBy));

        when(citizenRepository.existsByNationalId("1234567890123456")).thenReturn(false);
        when(citizenRepository.existsByPhone("01012345678")).thenReturn(false);

        Citizen saved = new Citizen();
        saved.setId(UUID.randomUUID());
        saved.setFullName("أحمد");
        saved.setFullNameNormalized("احمد");
        saved.setNationalId("1234567890123456");
        saved.setPhone("01012345678");
        saved.setEmail(null);
        saved.setPreferredLanguage("en");
        when(citizenRepository.save(org.mockito.ArgumentMatchers.any(Citizen.class)))
                .thenReturn(saved);

        var request = new CreateCitizenRequest();
        request.setFullName("أحمد");
        request.setNationalId("1234567890123456");
        request.setPhone("+201012345678");
        request.setEmail("");
        request.setPreferredLanguage("en");

        CitizenResponse response = citizenService.createCitizen(request, requesterId);

        assertThat(response.getFullName()).isEqualTo("أحمد");
        verify(citizenRepository).save(org.mockito.ArgumentMatchers.argThat(c ->
                "01012345678".equals(c.getPhone()) &&
                "احمد".equals(c.getFullNameNormalized())
        ));
    }

    // ── masking in search results ───────────────────────────────────────

    @Test
    void searchCitizens_masksSensitiveFields_forAllRoles() {
        UUID agentId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        CitizenSearchRequest request = new CitizenSearchRequest();
        request.setSearchTerm("test");
        request.setPage(0);
        request.setSize(10);

        Citizen citizen = new Citizen();
        citizen.setId(UUID.randomUUID());
        citizen.setFullName("Ahmed Ali");
        citizen.setFullNameNormalized("ahmed ali");
        citizen.setNationalId("1234567890123456");
        citizen.setPhone("01012345678");
        citizen.setEmail("ahmed@test.gov");

        Page<Citizen> page = new PageImpl<>(List.of(citizen), PageRequest.of(0, 10), 1);
        when(citizenRepository.searchCitizens("test", "test", null, PageRequest.of(0, 10)))
                .thenReturn(page);
        List<Object[]> counts = new ArrayList<>();
        counts.add(new Object[]{citizen.getId(), 0L});
        when(citizenRepository.countCasesByCitizenIds(List.of(citizen.getId())))
                .thenReturn(counts);

        // AGENT — all sensitive fields masked
        AppUser agent = new AppUser();
        agent.setId(agentId);
        agent.setRole(UserRole.AGENT);
        when(appUserRepository.findById(agentId)).thenReturn(Optional.of(agent));

        var agentResult = citizenService.searchCitizens(request, agentId);
        assertThat(agentResult.getContent().get(0).getNationalId()).isEqualTo("123****3456");
        assertThat(agentResult.getContent().get(0).getPhone()).isEqualTo("010****5678");
        assertThat(agentResult.getContent().get(0).getEmail()).isEqualTo("a****@test.gov");

        // ADMIN — search results are ALSO masked for all roles per decision table
        CitizenSearchRequest adminRequest = new CitizenSearchRequest();
        adminRequest.setSearchTerm("test");
        adminRequest.setPage(0);
        adminRequest.setSize(10);
        AppUser admin = new AppUser();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);
        when(appUserRepository.findById(adminId)).thenReturn(Optional.of(admin));

        var adminResult = citizenService.searchCitizens(adminRequest, adminId);
        assertThat(adminResult.getContent().get(0).getNationalId()).isEqualTo("123****3456");
        assertThat(adminResult.getContent().get(0).getPhone()).isEqualTo("010****5678");
        assertThat(adminResult.getContent().get(0).getEmail()).isEqualTo("a****@test.gov");
    }

    // ── masking in get by id ────────────────────────────────────────────

    @Test
    void getCitizenById_masksForAgent_fullyForAdmin() {
        UUID agentId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        citizen.setNationalId("1234567890123456");
        citizen.setPhone("01012345678");
        citizen.setEmail("ahmed@test.gov");

        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        when(caseRepository.countVisibleByCitizenId(citizenId, agentId, null)).thenReturn(0L);
        when(appUserRepository.findById(agentId)).thenReturn(Optional.of(
                buildUser(agentId, UserRole.AGENT)));

        CitizenResponse agentResponse = citizenService.getCitizenById(citizenId, agentId);
        assertThat(agentResponse.getNationalId()).isEqualTo("123****3456");
        assertThat(agentResponse.getPhone()).isEqualTo("010****5678");
        assertThat(agentResponse.getEmail()).isEqualTo("a****@test.gov");
        assertThat(agentResponse.getFullName()).isEqualTo("Citizen One"); // never masked

        when(caseRepository.countVisibleByCitizenId(citizenId, null, null)).thenReturn(2L);
        when(appUserRepository.findById(adminId)).thenReturn(Optional.of(
                buildUser(adminId, UserRole.ADMIN)));

        CitizenResponse adminResponse = citizenService.getCitizenById(citizenId, adminId);
        assertThat(adminResponse.getNationalId()).isEqualTo("1234567890123456");
        assertThat(adminResponse.getPhone()).isEqualTo("01012345678");
        assertThat(adminResponse.getEmail()).isEqualTo("ahmed@test.gov");
    }

    // ── masking in profile view ─────────────────────────────────────────

    @Test
    void getCitizenProfile_masksForAgent_fullForSupervisor() {
        UUID agentId = UUID.randomUUID();
        UUID supervisorId = UUID.randomUUID();

        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        when(appUserRepository.findById(agentId)).thenReturn(Optional.of(
                buildUser(agentId, UserRole.AGENT)));
        when(caseRepository.countVisibleByCitizenIdByStatus(citizenId, agentId, null))
                .thenReturn(List.of());
        when(caseRepository.findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, agentId, null, PageRequest.of(0, 5)))
                .thenReturn(List.of());

        CitizenProfileResponse agentProfile = citizenService.getCitizenProfile(citizenId, agentId);
        assertThat(agentProfile.getNationalId()).isEqualTo("123****3456");
        assertThat(agentProfile.getPhone()).isEqualTo("010****5678");
        assertThat(agentProfile.getEmail()).isEqualTo("c****@test.gov");
        assertThat(agentProfile.getFullName()).isEqualTo("Citizen One");

        when(appUserRepository.findById(supervisorId)).thenReturn(Optional.of(
                buildUser(supervisorId, UserRole.SUPERVISOR)));
        when(caseRepository.countVisibleByCitizenIdByStatus(citizenId, null, null))
                .thenReturn(List.of());
        when(caseRepository.findVisibleByCitizenIdOrderByCreatedAtDesc(
                citizenId, null, null, PageRequest.of(0, 5)))
                .thenReturn(List.of());

        CitizenProfileResponse supervisorProfile = citizenService.getCitizenProfile(citizenId, supervisorId);
        assertThat(supervisorProfile.getNationalId()).isEqualTo("1234567890123456");
        assertThat(supervisorProfile.getPhone()).isEqualTo("01012345678");
        assertThat(supervisorProfile.getEmail()).isEqualTo("citizen@test.gov");
    }

    private AppUser buildUser(UUID id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}


