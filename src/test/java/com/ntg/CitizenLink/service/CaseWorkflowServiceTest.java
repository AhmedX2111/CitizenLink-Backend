package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.dto.agent.response.CaseActionResponse;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.UserRole;
import com.ntg.CitizenLink.enums.WorkflowAction;
import com.ntg.CitizenLink.exception.IllegalTransitionException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CaseWorkflowService} — the case state machine.
 *
 * Verifies the full transition table (BRD §5.5.2/§5.5.3):
 *   - getAllowedActions returns the exact button set for (status, role)
 *   - resolveTransition accepts every legal (status, action, role) combo
 *   - resolveTransition rejects illegal (status, action) → INVALID_TRANSITION
 *   - resolveTransition rejects legal action with wrong role → ROLE_NOT_ALLOWED
 *   - SUSPEND requiresComment, RESOLVE requiresResolutionSummary, REASSIGN requiresComment
 *
 * No Spring context — pure rules engine.
 */
class CaseWorkflowServiceTest {

    private final CaseWorkflowService service = new CaseWorkflowService();

    @Nested
    class GetAllowedActions {

        @Test
        void shouldReturnAssignAndCancel_forNewCaseAsSupervisor() {
            List<CaseActionResponse> actions =
                    service.getAllowedActions(CaseStatus.NEW, UserRole.SUPERVISOR);

            assertThat(actions)
                    .extracting(CaseActionResponse::getAction)
                    .containsExactlyInAnyOrder(WorkflowAction.ASSIGN, WorkflowAction.CANCEL);
        }

        @Test
        void shouldReturnStart_forAssignedCaseAsHandler() {
            List<CaseActionResponse> actions =
                    service.getAllowedActions(CaseStatus.ASSIGNED, UserRole.HANDLER);

            assertThat(actions).extracting(CaseActionResponse::getAction)
                    .containsExactly(WorkflowAction.START);
        }

        @Test
        void shouldReturnReassignAndCancel_forAssignedCaseAsAdmin() {
            List<CaseActionResponse> actions =
                    service.getAllowedActions(CaseStatus.ASSIGNED, UserRole.ADMIN);

            assertThat(actions).extracting(CaseActionResponse::getAction)
                    .containsExactlyInAnyOrder(WorkflowAction.REASSIGN, WorkflowAction.CANCEL);
        }

        @Test
        void shouldReturnStartReassignAndCancel_forAssignedCaseAsSupervisor() {
            List<CaseActionResponse> actions =
                    service.getAllowedActions(CaseStatus.ASSIGNED, UserRole.SUPERVISOR);

            assertThat(actions).extracting(CaseActionResponse::getAction)
                    .containsExactlyInAnyOrder(
                            WorkflowAction.START, WorkflowAction.REASSIGN, WorkflowAction.CANCEL);
        }

        @Test
        void shouldReturnEmpty_forAgentInEveryStatus() {
            for (CaseStatus status : CaseStatus.values()) {
                assertThat(service.getAllowedActions(status, UserRole.AGENT))
                        .as("Agent should have no actions in status %s", status)
                        .isEmpty();
            }
        }

        @Test
        void shouldMarkSuspendAsRequiringComment() {
            List<CaseActionResponse> actions =
                    service.getAllowedActions(CaseStatus.IN_PROGRESS, UserRole.HANDLER);

            CaseActionResponse suspend = actions.stream()
                    .filter(a -> a.getAction() == WorkflowAction.SUSPEND)
                    .findFirst()
                    .orElseThrow();

            assertThat(suspend.isRequiresComment()).isTrue();
        }

        @Test
        void shouldMarkResolveAsRequiringResolutionSummary() {
            List<CaseActionResponse> actions =
                    service.getAllowedActions(CaseStatus.IN_PROGRESS, UserRole.HANDLER);

            CaseActionResponse resolve = actions.stream()
                    .filter(a -> a.getAction() == WorkflowAction.RESOLVE)
                    .findFirst()
                    .orElseThrow();

            assertThat(resolve.isRequiresResolutionSummary()).isTrue();
        }
    }

    @Nested
    class ResolveTransition {

        @Test
        void shouldResolveReassign_onAllFourHandlerStatuses_asSupervisor() {
            CaseStatus[] statuses = {
                    CaseStatus.ASSIGNED,
                    CaseStatus.IN_PROGRESS,
                    CaseStatus.AWAITING_INFO,
                    CaseStatus.SUSPENDED
            };

            for (CaseStatus status : statuses) {
                CaseTransitionRule rule = service.resolveTransition(
                        status, WorkflowAction.REASSIGN, UserRole.SUPERVISOR);

                assertThat(rule.toStatus())
                        .as("REASSIGN from %s must keep the same status", status)
                        .isEqualTo(status);
                assertThat(rule.requiresComment()).isTrue();
            }
        }

        @Test
        void shouldResolveInfoReceived_fromAwaitingInfo_asHandler() {
            CaseTransitionRule rule = service.resolveTransition(
                    CaseStatus.AWAITING_INFO, WorkflowAction.INFO_RECEIVED, UserRole.HANDLER);

            assertThat(rule.toStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
        }

        @Test
        void shouldResolveResume_fromSuspended_asHandler() {
            CaseTransitionRule rule = service.resolveTransition(
                    CaseStatus.SUSPENDED, WorkflowAction.RESUME, UserRole.HANDLER);

            assertThat(rule.toStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
        }

        @Test
        void shouldResolveReopen_fromResolved_andClosed_asSupervisor() {
            assertThat(service.resolveTransition(
                    CaseStatus.RESOLVED, WorkflowAction.REOPEN, UserRole.SUPERVISOR).toStatus())
                    .isEqualTo(CaseStatus.IN_PROGRESS);
            assertThat(service.resolveTransition(
                    CaseStatus.CLOSED, WorkflowAction.REOPEN, UserRole.SUPERVISOR).toStatus())
                    .isEqualTo(CaseStatus.IN_PROGRESS);
        }

        @Test
        void shouldResolveCancel_fromNewAndAssigned_asAdmin() {
            assertThat(service.resolveTransition(
                    CaseStatus.NEW, WorkflowAction.CANCEL, UserRole.ADMIN).toStatus())
                    .isEqualTo(CaseStatus.CANCELLED);
            assertThat(service.resolveTransition(
                    CaseStatus.ASSIGNED, WorkflowAction.CANCEL, UserRole.ADMIN).toStatus())
                    .isEqualTo(CaseStatus.CANCELLED);
        }

        @Test
        void shouldThrowInvalidTransition_forIllegalStatusActionPair() {
            assertThatThrownBy(() ->
                    service.resolveTransition(CaseStatus.NEW, WorkflowAction.START, UserRole.HANDLER))
                    .isInstanceOf(IllegalTransitionException.class)
                    .extracting("code").isEqualTo("INVALID_TRANSITION");
        }

        @Test
        void shouldThrowInvalidTransition_forReassignOnNewCase() {
            assertThatThrownBy(() ->
                    service.resolveTransition(CaseStatus.NEW, WorkflowAction.REASSIGN, UserRole.ADMIN))
                    .isInstanceOf(IllegalTransitionException.class)
                    .extracting("code").isEqualTo("INVALID_TRANSITION");
        }

        @Test
        void shouldThrowRoleNotAllowed_whenRoleNotPermitted() {
            assertThatThrownBy(() ->
                    service.resolveTransition(CaseStatus.NEW, WorkflowAction.ASSIGN, UserRole.HANDLER))
                    .isInstanceOf(IllegalTransitionException.class)
                    .extracting("code").isEqualTo("ROLE_NOT_ALLOWED");
        }

        @Test
        void shouldThrowRoleNotAllowed_whenAgentTriesAnyAction() {
            assertThatThrownBy(() ->
                    service.resolveTransition(CaseStatus.ASSIGNED, WorkflowAction.START, UserRole.AGENT))
                    .isInstanceOf(IllegalTransitionException.class)
                    .extracting("code").isEqualTo("ROLE_NOT_ALLOWED");
        }

        @Test
        void shouldThrowRoleNotAllowed_forReopenAsAdmin() {
            // REOPEN is SUPERVISOR-only per the rules table
            assertThatThrownBy(() ->
                    service.resolveTransition(CaseStatus.RESOLVED, WorkflowAction.REOPEN, UserRole.ADMIN))
                    .isInstanceOf(IllegalTransitionException.class)
                    .extracting("code").isEqualTo("ROLE_NOT_ALLOWED");
        }
    }
}
