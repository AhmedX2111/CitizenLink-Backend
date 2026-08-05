package com.ntg.CitizenLink.security;

import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CaseAccessPolicy} — the single source of truth for
 * case visibility.
 *
 * Rule under test:
 *   - ADMIN, SUPERVISOR → any case
 *   - HANDLER           → only cases assigned to them
 *   - AGENT             → only cases they created
 *
 * No Spring context — pure logic on entity graphs.
 */
class CaseAccessPolicyTest {

    private final CaseAccessPolicy policy = new CaseAccessPolicy();

    private AppUser admin;
    private AppUser supervisor;
    private AppUser handler;
    private AppUser agent;
    private AppUser anotherHandler;
    private AppUser anotherAgent;

    @BeforeEach
    void setUp() {
        admin = user(UserRole.ADMIN);
        supervisor = user(UserRole.SUPERVISOR);
        handler = user(UserRole.HANDLER);
        agent = user(UserRole.AGENT);
        anotherHandler = user(UserRole.HANDLER);
        anotherAgent = user(UserRole.AGENT);
    }

    private AppUser user(UserRole role) {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setUsername("u-" + role);
        u.setRole(role);
        return u;
    }

    private Case aCase(AppUser creator, AppUser assigned) {
        Case c = new Case();
        c.setId(UUID.randomUUID());
        c.setCreatedByUser(creator);
        c.setAssignedToUser(assigned);
        return c;
    }

    @Nested
    class AdminAndSupervisor {

        @Test
        void shouldViewAnyCase_asAdmin() {
            Case c = aCase(anotherAgent, anotherHandler);

            assertThat(policy.canView(c, admin)).isTrue();
        }

        @Test
        void shouldViewAnyCase_asSupervisor() {
            Case c = aCase(anotherAgent, anotherHandler);

            assertThat(policy.canView(c, supervisor)).isTrue();
        }
    }

    @Nested
    class Handler {

        @Test
        void shouldViewCaseAssignedToSelf() {
            Case c = aCase(agent, handler);

            assertThat(policy.canView(c, handler)).isTrue();
        }

        @Test
        void shouldNotViewCaseAssignedToSomeoneElse() {
            Case c = aCase(agent, anotherHandler);

            assertThat(policy.canView(c, handler)).isFalse();
        }

        @Test
        void shouldNotViewUnassignedCase() {
            Case c = aCase(agent, null);

            assertThat(policy.canView(c, handler)).isFalse();
        }
    }

    @Nested
    class Agent {

        @Test
        void shouldViewCaseTheyCreated() {
            Case c = aCase(agent, handler);

            assertThat(policy.canView(c, agent)).isTrue();
        }

        @Test
        void shouldNotViewCaseCreatedByAnotherAgent() {
            Case c = aCase(anotherAgent, handler);

            assertThat(policy.canView(c, agent)).isFalse();
        }
    }
}
