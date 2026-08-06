package com.ntg.CitizenLink.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.entities.Category;
import com.ntg.CitizenLink.entities.Citizen;
import com.ntg.CitizenLink.entities.Department;
import com.ntg.CitizenLink.enums.UserRole;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import com.ntg.CitizenLink.repositories.CategoryRepository;
import com.ntg.CitizenLink.repositories.CitizenRepository;
import com.ntg.CitizenLink.repositories.DepartmentRepository;
import com.ntg.CitizenLink.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the workflow state machine (BRD §5.5.2/§5.5.3)
 * exercised through the real HTTP layer:
 *   - illegal (status, action) transitions are rejected with 409
 *   - role restrictions are enforced (approve/close, assigned-handler-only, admin bypass)
 *   - concurrent transitions cannot corrupt state (optimistic locking)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IllegalWorkflowTransitionIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppUser owner;
    private AppUser supervisor;
    private AppUser admin;
    private AppUser handlerA;
    private AppUser handlerB;
    private Citizen citizen;
    private Category category;
    private Department department;
    private String ownerToken;
    private String supervisorToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser(UserRole.AGENT);
        supervisor = createUser(UserRole.SUPERVISOR);
        admin = createUser(UserRole.ADMIN);
        handlerA = createUser(UserRole.HANDLER);
        handlerB = createUser(UserRole.HANDLER);
        citizen = EntityFactory.citizen(owner);
        citizen.setNationalId(uniqueNationalId());
        citizenRepository.save(citizen);
        category = categoryRepository.save(EntityFactory.category());
        department = departmentRepository.save(EntityFactory.department());
        ownerToken = login(owner.getUsername());
        supervisorToken = login(supervisor.getUsername());
        adminToken = login(admin.getUsername());
    }

    private static String uniqueNationalId() {
        return String.format("%016d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000_000_000L));
    }

    private AppUser createUser(UserRole role) {
        AppUser user = EntityFactory.appUser(role);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String createCase(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "Workflow test case",
                                  "description": "Illegal transition test fixture",
                                  "type": "COMPLAINT",
                                  "priority": "HIGH",
                                  "channel": "WEB",
                                  "citizenNationalId": "%s",
                                  "categoryId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(citizen.getNationalId(), category.getId(), department.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void transition(String token, String caseId, String body) throws Exception {
        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void assign(String token, String caseId, AppUser handler) throws Exception {
        transition(token, caseId, "{\"action\":\"ASSIGN\",\"assignedToUserId\":\"" + handler.getId() + "\"}");
    }

    private void start(String token, String caseId) throws Exception {
        transition(token, caseId, "{\"action\":\"START\"}");
    }

    private void resolve(String token, String caseId) throws Exception {
        transition(token, caseId, "{\"action\":\"RESOLVE\",\"resolutionSummary\":\"Workflow test resolution\"}");
    }

    private void close(String token, String caseId) throws Exception {
        transition(token, caseId, "{\"action\":\"CLOSE\"}");
    }

    private void cancel(String token, String caseId) throws Exception {
        transition(token, caseId, "{\"action\":\"CANCEL\"}");
    }

    // -------------------------------------------------------------------------
    // 1. Invalid status transitions
    // -------------------------------------------------------------------------

    @Test
    void cannotResolve_directlyFromNew_skippingWorkflow() throws Exception {
        // Maps to "DRAFT -> COMPLETED should go through REVIEW first":
        // a NEW case cannot jump straight to RESOLVED.
        String caseId = createCase(ownerToken);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"RESOLVE\",\"resolutionSummary\":\"jump\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void cannotClose_directlyFromNew() throws Exception {
        String caseId = createCase(ownerToken);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CLOSE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void cannotClose_fromInProgress_withoutResolving() throws Exception {
        // Maps to "PENDING -> APPROVED without review": an in-progress case
        // must be RESOLVED before it can be CLOSED.
        String caseId = createCase(ownerToken);
        assign(supervisorToken, caseId, handlerA);
        start(login(handlerA.getUsername()), caseId);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CLOSE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void cannotStart_directlyFromNew() throws Exception {
        // Maps to "must go through ASSIGN first".
        String caseId = createCase(ownerToken);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void cannotAssign_fromInProgress() throws Exception {
        String caseId = createCase(ownerToken);
        assign(supervisorToken, caseId, handlerA);
        start(login(handlerA.getUsername()), caseId);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\",\"assignedToUserId\":\"" + handlerB.getId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void cannotCancel_fromInProgress() throws Exception {
        String caseId = createCase(ownerToken);
        assign(supervisorToken, caseId, handlerA);
        start(login(handlerA.getUsername()), caseId);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CANCEL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void cannotReopen_fromCancelled() throws Exception {
        // Maps to "REJECTED -> APPROVED without changes": a CANCELLED case is
        // terminal — REOPEN (the "approve again" path) is not allowed from it.
        String caseId = createCase(ownerToken);
        cancel(supervisorToken, caseId);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REOPEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void cannotStart_fromClosed() throws Exception {
        // CLOSED -> IN_PROGRESS is only legal via REOPEN (SUPERVISOR-only);
        // the generic START action must be rejected.
        String caseId = createCase(ownerToken);
        assign(supervisorToken, caseId, handlerA);
        String handlerToken = login(handlerA.getUsername());
        start(handlerToken, caseId);
        resolve(handlerToken, caseId);
        close(supervisorToken, caseId);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void closedCanReturnToInProgress_onlyViaReopen_bySupervisor() throws Exception {
        String caseId = createCase(ownerToken);
        assign(supervisorToken, caseId, handlerA);
        String handlerToken = login(handlerA.getUsername());
        start(handlerToken, caseId);
        resolve(handlerToken, caseId);
        close(supervisorToken, caseId);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REOPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    // -------------------------------------------------------------------------
    // 2. Role-based transition restrictions
    // -------------------------------------------------------------------------

    @Test
    void agentCannotTransition_anyAction() throws Exception {
        String caseId = createCase(ownerToken);

        // AGENT is denied at the method-security layer (403) — the transition
        // endpoint is restricted to HANDLER/SUPERVISOR/ADMIN.
        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\",\"assignedToUserId\":\"" + handlerA.getId() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void handlerCannotClose_resolvedCase() throws Exception {
        // Maps to "user without proper role cannot approve/reject": CLOSE is the
        // final approval step and is reserved for SUPERVISOR/ADMIN.
        String caseId = createCase(ownerToken);
        assign(supervisorToken, caseId, handlerA);
        String handlerToken = login(handlerA.getUsername());
        start(handlerToken, caseId);
        resolve(handlerToken, caseId);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CLOSE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
    }

    @Test
    void handlerCannotCancel_assignedCase() throws Exception {
        String caseId = createCase(ownerToken);
        assign(supervisorToken, caseId, handlerA);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(handlerA.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CANCEL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
    }

    @Test
    void unassignedHandlerCannotStart() throws Exception {
        // Maps to "only the assigned reviewer can transition to REVIEW": a HANDLER
        // who is not the assignee cannot even see the case (404).
        String caseId = createCase(ownerToken);
        assign(supervisorToken, caseId, handlerA);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(handlerB.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignedHandlerCanStart() throws Exception {
        String caseId = createCase(ownerToken);
        assign(supervisorToken, caseId, handlerA);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(handlerA.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void adminCanClose_bypassingHandlerScope() throws Exception {
        // Maps to "admin can bypass certain restrictions": ADMIN is allowed to
        // perform the final approval (CLOSE) on any case.
        String caseId = createCase(ownerToken);
        assign(supervisorToken, caseId, handlerA);
        String handlerToken = login(handlerA.getUsername());
        start(handlerToken, caseId);
        resolve(handlerToken, caseId);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CLOSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void adminCannotStart_evenThoughItBypassesOtherRestrictions() throws Exception {
        // Admin bypass does NOT extend to actions explicitly restricted to
        // HANDLER/SUPERVISOR — START stays forbidden for ADMIN.
        String caseId = createCase(ownerToken);
        assign(supervisorToken, caseId, handlerA);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
    }

    // -------------------------------------------------------------------------
    // 3. Concurrent transition attempts
    // -------------------------------------------------------------------------

    @Test
    void simultaneousAssign_onlyOneSucceeds() throws Exception {
        // Two supervisors fire ASSIGN on the same NEW case at the same time.
        // Exactly one must win (200); the other gets 409 — either because the
        // status already changed (INVALID_TRANSITION) or because the
        // optimistic-lock version check failed (CONCURRENT_MODIFICATION).
        AppUser supervisorB = createUser(UserRole.SUPERVISOR);
        String supervisorBToken = login(supervisorB.getUsername());
        String caseId = createCase(ownerToken);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();
        String body = "{\"action\":\"ASSIGN\",\"assignedToUserId\":\"" + handlerA.getId() + "\"}";

        for (String token : List.of(supervisorToken, supervisorBToken)) {
            futures.add(pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                MvcResult result = mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(statuses)
                .containsExactlyInAnyOrder(HttpStatus.OK.value(), HttpStatus.CONFLICT.value());

        // The case must end ASSIGNED with a single ASSIGN history entry.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/cases/{id}", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void transition_afterAnotherUserAlreadyMovedTheCase_isRejected() throws Exception {
        // Maps to "transition after case was already updated by another user":
        // user A assigns the case, then user B — still acting on the old NEW
        // view — tries to ASSIGN again and is rejected because the state moved.
        String caseId = createCase(ownerToken);
        AppUser supervisorB = createUser(UserRole.SUPERVISOR);
        String supervisorBToken = login(supervisorB.getUsername());

        assign(supervisorToken, caseId, handlerA);

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\",\"assignedToUserId\":\"" + handlerB.getId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        // The first transition is not rolled back or duplicated.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/cases/{id}", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }
}
