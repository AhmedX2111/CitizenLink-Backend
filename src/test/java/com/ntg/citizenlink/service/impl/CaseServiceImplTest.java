package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.dto.agent.request.CaseTransitionRequest;
import com.ntg.citizenlink.dto.agent.request.CreateCaseRequest;
import com.ntg.citizenlink.dto.agent.response.CaseResponse;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.entities.Category;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.entities.Department;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.enums.WorkflowAction;
import com.ntg.citizenlink.exception.BusinessRuleException;
import com.ntg.citizenlink.exception.IllegalTransitionException;
import com.ntg.citizenlink.exception.ResourceNotFoundException;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.CaseRepository;
import com.ntg.citizenlink.repositories.CategoryRepository;
import com.ntg.citizenlink.repositories.CitizenRepository;
import com.ntg.citizenlink.repositories.DepartmentRepository;
import com.ntg.citizenlink.repositories.StatusHistoryRepository;
import com.ntg.citizenlink.security.CaseAccessPolicy;
import com.ntg.citizenlink.service.CaseTransitionRule;
import com.ntg.citizenlink.service.CaseWorkflowService;
import com.ntg.citizenlink.service.interfaces.CaseNumberService;
import com.ntg.citizenlink.service.mapper.CaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CaseServiceImpl#transitionCase} — the core workflow
 * execution path.
 * Covers: happy-path transition (+StatusHistory row), REASSIGN (assignee swap,
 * status unchanged), conditional-field enforcement (comment for SUSPEND,
 * resolution summary for RESOLVE), ASSIGN/REASSIGN target validation
 * (missing id, wrong role, inactive target), and the 404 guards
 * (missing case, missing requester, no view permission).
 * NOT covered: search/create/timeline/actions (kept for Phase 3/5) and the
 * rules table itself (see CaseWorkflowServiceTest).
 */
@ExtendWith(MockitoExtension.class)
class CaseServiceImplTest {

    @Mock private CaseRepository caseRepository;
    @Mock private AppUserRepository userRepository;
    @Mock private CitizenRepository citizenRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private StatusHistoryRepository statusHistoryRepository;
    @Mock private CaseNumberService caseNumberService;
    @Mock private CaseMapper caseMapper;
    @Mock private CaseAccessPolicy caseAccessPolicy;
    @Mock private CaseWorkflowService caseWorkflowService;

    @InjectMocks private CaseServiceImpl caseService;

    private UUID caseId;
    private UUID requesterId;
    private AppUser supervisor;
    private AppUser handler;
    private AppUser oldHandler;
    private Case aCase;

    @BeforeEach
    void setUp() {
        caseId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        supervisor = user(UserRole.SUPERVISOR);
        handler = user(UserRole.HANDLER);
        oldHandler = user(UserRole.HANDLER);

        aCase = new Case();
        aCase.setId(caseId);
        aCase.setCaseNumber("CASE-2026-00001");
        aCase.setStatus(CaseStatus.IN_PROGRESS);
        aCase.setAssignedToUser(oldHandler);
        aCase.setCreatedByUser(user(UserRole.AGENT));
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

    private CaseTransitionRequest request(WorkflowAction action) {
        CaseTransitionRequest r = new CaseTransitionRequest();
        r.setAction(action);
        return r;
    }

    private void stubVisibility() {
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(aCase));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(supervisor));
        when(caseAccessPolicy.canView(aCase, supervisor)).thenReturn(true);
    }

    private void stubSave() {
        when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubMapper() {
        when(caseMapper.toResponse(any())).thenReturn(mock(CaseResponse.class));
    }

    private void stubRule(CaseStatus toStatus, boolean requiresComment,
                          boolean requiresResolutionSummary) {
        CaseTransitionRule rule = new CaseTransitionRule(
                WorkflowAction.START, CaseStatus.ASSIGNED, toStatus,
                Set.of(UserRole.SUPERVISOR, UserRole.ADMIN),
                "cases.actions.start", requiresComment, requiresResolutionSummary);
        when(caseWorkflowService.resolveTransition(any(), any(), any())).thenReturn(rule);
    }

    @Nested
    class ValidTransitions {

        @Test
        void shouldUpdateStatus_andWriteHistory_onValidTransition() {
            stubVisibility();
            stubSave();
            stubMapper();
            stubRule(CaseStatus.IN_PROGRESS, false, false);

            caseService.transitionCase(caseId, requesterId, request(WorkflowAction.START));

            ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
            verify(caseRepository).save(caseCaptor.capture());
            assertThat(caseCaptor.getValue().getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);

            ArgumentCaptor<com.ntg.citizenlink.entities.StatusHistory> historyCaptor =
                    ArgumentCaptor.forClass(com.ntg.citizenlink.entities.StatusHistory.class);
            verify(statusHistoryRepository).save(historyCaptor.capture());
            assertThat(historyCaptor.getValue().getAction()).isEqualTo(WorkflowAction.START);
            assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
            assertThat(historyCaptor.getValue().getChangedByUser()).isEqualTo(supervisor);
        }

        @Test
        void shouldSetResolvedAt_whenTransitioningToResolved() {
            stubVisibility();
            stubSave();
            stubMapper();
            stubRule(CaseStatus.RESOLVED, false, true);
            CaseTransitionRequest r = request(WorkflowAction.RESOLVE);
            r.setResolutionSummary("fixed");

            caseService.transitionCase(caseId, requesterId, r);

            ArgumentCaptor<Case> captor = ArgumentCaptor.forClass(Case.class);
            verify(caseRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(CaseStatus.RESOLVED);
            assertThat(captor.getValue().getResolvedAt()).isNotNull();
            assertThat(captor.getValue().getResolutionSummary()).isEqualTo("fixed");
        }
    }

    @Nested
    class Reassign {

        @Test
        void shouldSwapHandler_withoutChangingStatus() {
            stubVisibility();
            stubSave();
            stubMapper();
            CaseTransitionRule reassignRule = new CaseTransitionRule(
                    WorkflowAction.REASSIGN, CaseStatus.IN_PROGRESS, CaseStatus.IN_PROGRESS,
                    Set.of(UserRole.SUPERVISOR, UserRole.ADMIN),
                    "cases.actions.reassign", true, false);
            when(caseWorkflowService.resolveTransition(any(), any(), any())).thenReturn(reassignRule);

            when(userRepository.findById(handler.getId())).thenReturn(Optional.of(handler));

            CaseTransitionRequest r = request(WorkflowAction.REASSIGN);
            r.setAssignedToUserId(handler.getId());
            r.setComment("handover");

            caseService.transitionCase(caseId, requesterId, r);

            ArgumentCaptor<Case> captor = ArgumentCaptor.forClass(Case.class);
            verify(caseRepository).save(captor.capture());
            assertThat(captor.getValue().getAssignedToUser()).isEqualTo(handler);
            assertThat(captor.getValue().getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
        }

        @Test
        void shouldThrowInvalidReassignment_whenTargetIdMissing() {
            stubVisibility();
            CaseTransitionRule reassignRule = new CaseTransitionRule(
                    WorkflowAction.REASSIGN, CaseStatus.IN_PROGRESS, CaseStatus.IN_PROGRESS,
                    Set.of(UserRole.SUPERVISOR, UserRole.ADMIN),
                    "cases.actions.reassign", true, false);
            when(caseWorkflowService.resolveTransition(any(), any(), any())).thenReturn(reassignRule);

            CaseTransitionRequest r = request(WorkflowAction.REASSIGN);
            r.setComment("handover");

            assertThatThrownBy(() -> caseService.transitionCase(caseId, requesterId, r))
                    .isInstanceOf(IllegalTransitionException.class)
                    .extracting("code").isEqualTo("INVALID_REASSIGNMENT");
        }

        @Test
        void shouldThrowInvalidReassignment_whenTargetNotHandler() {
            stubVisibility();
            CaseTransitionRule reassignRule = new CaseTransitionRule(
                    WorkflowAction.REASSIGN, CaseStatus.IN_PROGRESS, CaseStatus.IN_PROGRESS,
                    Set.of(UserRole.SUPERVISOR, UserRole.ADMIN),
                    "cases.actions.reassign", true, false);
            when(caseWorkflowService.resolveTransition(any(), any(), any())).thenReturn(reassignRule);

            AppUser agent = user(UserRole.AGENT);
            when(userRepository.findById(agent.getId())).thenReturn(Optional.of(agent));

            CaseTransitionRequest r = request(WorkflowAction.REASSIGN);
            r.setAssignedToUserId(agent.getId());
            r.setComment("handover");

            assertThatThrownBy(() -> caseService.transitionCase(caseId, requesterId, r))
                    .isInstanceOf(IllegalTransitionException.class)
                    .extracting("code").isEqualTo("INVALID_REASSIGNMENT");
        }
    }

    @Nested
    class ConditionalFields {

        @Test
        void shouldThrowMissingComment_forSuspendWithoutComment() {
            stubVisibility();
            stubRule(CaseStatus.SUSPENDED, true, false);

            CaseTransitionRequest r = request(WorkflowAction.SUSPEND);
            assertThatThrownBy(() -> caseService.transitionCase(caseId, requesterId, r))
                    .isInstanceOf(IllegalTransitionException.class)
                    .extracting("code").isEqualTo("MISSING_COMMENT");

            verify(caseRepository, never()).save(any());
        }

        @Test
        void shouldThrowMissingResolutionSummary_forResolveWithoutSummary() {
            stubVisibility();
            stubRule(CaseStatus.RESOLVED, false, true);

            CaseTransitionRequest r = request(WorkflowAction.RESOLVE);
            assertThatThrownBy(() -> caseService.transitionCase(caseId, requesterId, r))
                    .isInstanceOf(IllegalTransitionException.class)
                    .extracting("code").isEqualTo("MISSING_RESOLUTION_SUMMARY");

            verify(caseRepository, never()).save(any());
        }
    }

    @Nested
    class Guards {

        @Test
        void shouldThrowNotFound_whenCaseMissing() {
            when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

            CaseTransitionRequest r = request(WorkflowAction.START);
            assertThatThrownBy(() -> caseService.transitionCase(caseId, requesterId, r))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void shouldThrowNotFound_whenRequesterMissing() {
            when(caseRepository.findById(caseId)).thenReturn(Optional.of(aCase));
            when(userRepository.findById(requesterId)).thenReturn(Optional.empty());

            CaseTransitionRequest r = request(WorkflowAction.START);
            assertThatThrownBy(() -> caseService.transitionCase(caseId, requesterId, r))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void shouldThrowNotFound_whenNoViewPermission() {
            when(caseRepository.findById(caseId)).thenReturn(Optional.of(aCase));
            when(userRepository.findById(requesterId)).thenReturn(Optional.of(supervisor));
            when(caseAccessPolicy.canView(aCase, supervisor)).thenReturn(false);

            CaseTransitionRequest r = request(WorkflowAction.START);
            assertThatThrownBy(() -> caseService.transitionCase(caseId, requesterId, r))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class CreateCase {

        private UUID creatorId;
        private AppUser agent;
        private AppUser supervisor;
        private AppUser handler;
        private AppUser targetAgent;
        private AppUser inactiveHandler;
        private Citizen citizen;
        private Category category;
        private Department department;

        @BeforeEach
        void setUp() {
            creatorId = UUID.randomUUID();
            agent = user(UserRole.AGENT);
            supervisor = user(UserRole.SUPERVISOR);
            handler = user(UserRole.HANDLER);
            targetAgent = user(UserRole.AGENT);
            inactiveHandler = user(UserRole.HANDLER);
            inactiveHandler.setActive(false);

            citizen = new Citizen();
            citizen.setId(UUID.randomUUID());
            category = new Category();
            category.setId(UUID.randomUUID());
            category.setActive(true);
            department = new Department();
            department.setId(UUID.randomUUID());
            department.setActive(true);
        }

        private void stubLookups(AppUser creator) {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(citizenRepository.findByNationalId(any())).thenReturn(Optional.of(citizen));
            when(categoryRepository.findById(any())).thenReturn(Optional.of(category));
            when(departmentRepository.findById(any())).thenReturn(Optional.of(department));
        }

        private void stubPersist() {
            when(caseNumberService.generateNext()).thenReturn("CASE-2026-00001");
            when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(caseMapper.toResponse(any())).thenReturn(mock(CaseResponse.class));
        }

        private CreateCaseRequest requestWithAssignment(UUID assignedToUserId) {
            CreateCaseRequest r = new CreateCaseRequest();
            r.setSubject("subject");
            r.setDescription("description");
            r.setType(com.ntg.citizenlink.enums.CaseType.COMPLAINT);
            r.setPriority(com.ntg.citizenlink.enums.Priority.HIGH);
            r.setChannel(com.ntg.citizenlink.enums.Channel.WEB);
            r.setCitizenNationalId("12345678901234");
            r.setCategoryId(category.getId());
            r.setDepartmentId(department.getId());
            r.setAssignedToUserId(assignedToUserId);
            return r;
        }

        @Test
        void agentAssignmentRequest_isIgnored_createdInNewStatus() {
            stubLookups(agent);
            stubPersist();

            caseService.createCase(requestWithAssignment(handler.getId()), creatorId);

            ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
            verify(caseRepository).save(caseCaptor.capture());
            assertThat(caseCaptor.getValue().getStatus()).isEqualTo(CaseStatus.NEW);
            assertThat(caseCaptor.getValue().getAssignedToUser()).isNull();

            // Only the CREATE timeline entry — no ASSIGN row is written.
            verify(statusHistoryRepository, times(1)).save(any());
            // The privileged target lookup must not even happen.
            verify(userRepository, never()).findById(handler.getId());
        }

        @Test
        void supervisorAssignmentToHandler_setsAssignedStatusAndAssignHistory() {
            stubLookups(supervisor);
            stubPersist();
            when(userRepository.findById(handler.getId())).thenReturn(Optional.of(handler));

            caseService.createCase(requestWithAssignment(handler.getId()), creatorId);

            ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
            verify(caseRepository).save(caseCaptor.capture());
            assertThat(caseCaptor.getValue().getStatus()).isEqualTo(CaseStatus.ASSIGNED);
            assertThat(caseCaptor.getValue().getAssignedToUser()).isEqualTo(handler);

            ArgumentCaptor<com.ntg.citizenlink.entities.StatusHistory> historyCaptor =
                    ArgumentCaptor.forClass(com.ntg.citizenlink.entities.StatusHistory.class);
            verify(statusHistoryRepository, times(2)).save(historyCaptor.capture());
            assertThat(historyCaptor.getAllValues())
                    .extracting(com.ntg.citizenlink.entities.StatusHistory::getAction)
                    .containsExactly(WorkflowAction.CREATE, WorkflowAction.ASSIGN);
        }

        @Test
        void supervisorAssignmentToNonHandler_isRejected() {
            stubLookups(supervisor);
            when(userRepository.findById(targetAgent.getId())).thenReturn(Optional.of(targetAgent));

            assertThatThrownBy(() -> caseService.createCase(requestWithAssignment(targetAgent.getId()), creatorId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("HANDLER role");
        }

        @Test
        void supervisorAssignmentToInactiveUser_isRejected() {
            stubLookups(supervisor);
            when(userRepository.findById(inactiveHandler.getId())).thenReturn(Optional.of(inactiveHandler));

            assertThatThrownBy(() -> caseService.createCase(requestWithAssignment(inactiveHandler.getId()), creatorId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("inactive");
        }
    }
}
