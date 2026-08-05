package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.dto.agent.request.CaseTransitionRequest;
import com.ntg.CitizenLink.dto.agent.response.CaseResponse;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.UserRole;
import com.ntg.CitizenLink.enums.WorkflowAction;
import com.ntg.CitizenLink.exception.IllegalTransitionException;
import com.ntg.CitizenLink.exception.ResourceNotFoundException;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import com.ntg.CitizenLink.repositories.CaseRepository;
import com.ntg.CitizenLink.repositories.StatusHistoryRepository;
import com.ntg.CitizenLink.security.CaseAccessPolicy;
import com.ntg.CitizenLink.service.CaseTransitionRule;
import com.ntg.CitizenLink.service.CaseWorkflowService;
import com.ntg.CitizenLink.service.interfaces.CaseNumberService;
import com.ntg.CitizenLink.service.mapper.CaseMapper;
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

            ArgumentCaptor<com.ntg.CitizenLink.entities.StatusHistory> historyCaptor =
                    ArgumentCaptor.forClass(com.ntg.CitizenLink.entities.StatusHistory.class);
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
}
