package com.ntg.citizenlink.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.entities.Category;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.entities.Department;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.CaseRepository;
import com.ntg.citizenlink.repositories.CategoryRepository;
import com.ntg.citizenlink.repositories.CitizenRepository;
import com.ntg.citizenlink.repositories.DepartmentRepository;
import com.ntg.citizenlink.repositories.StatusHistoryRepository;
import com.ntg.citizenlink.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-53 end-to-end: bulk reassignment through the REST API against a real
 * (H2) database. Covers the five acceptance criteria: role access,
 * active-HANDLER destination, ineligible-case rejection with explicit
 * per-case reporting, and the REASSIGN audit/timeline entry.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulkReassignIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CaseRepository caseRepository;
    @Autowired private StatusHistoryRepository statusHistoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppUser supervisor;
    private AppUser handlerA;
    private AppUser handlerB;
    private Citizen citizen;
    private Category category;
    private Department department;
    private String supervisorToken;

    @BeforeEach
    void setUp() throws Exception {
        supervisor = createUser(UserRole.SUPERVISOR);
        handlerA = createUser(UserRole.HANDLER);
        handlerB = createUser(UserRole.HANDLER);
        citizen = EntityFactory.citizen(supervisor);
        citizen.setNationalId(uniqueNationalId());
        citizenRepository.save(citizen);
        category = categoryRepository.save(EntityFactory.category());
        department = departmentRepository.save(EntityFactory.department());
        supervisorToken = login(supervisor.getUsername());
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

    private String createAssignedCase(String assigneeId) throws Exception {
        String body = """
                {
                  "subject": "Bulk reassign integration case",
                  "description": "Created by integration test",
                  "type": "COMPLAINT",
                  "priority": "HIGH",
                  "channel": "WEB",
                  "citizenNationalId": "%s",
                  "categoryId": "%s",
                  "departmentId": "%s",
                  "assignedToUserId": "%s"
                }
                """.formatted(citizen.getNationalId(), category.getId(), department.getId(), assigneeId);

        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String transition(String caseId, String action, String extra, String token) throws Exception {
        String body = extra == null || extra.isBlank()
                ? "{\"action\":\"" + action + "\"}"
                : "{\"action\":\"" + action + "\"," + extra + "}";
        MvcResult result = mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("status").asText();
    }

    private String bulkReassignBody(List<UUID> caseIds, UUID targetId) {
        String ids = caseIds.stream()
                .map(id -> "\"" + id + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return """
                {
                  "caseIds": [%s],
                  "assignedToUserId": "%s",
                  "comment": "balancing queue load"
                }
                """.formatted(ids, targetId);
    }

    private JsonNode bulkReassign(List<UUID> caseIds, UUID targetId, String token, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cases/bulk-reassign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkReassignBody(caseIds, targetId)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ── AC: only SUPERVISOR/ADMIN can access bulk reassignment ────────────

    @Test
    void handlerToken_isForbidden() throws Exception {
        String caseId = createAssignedCase(handlerA.getId().toString());
        String handlerToken = login(handlerA.getUsername());

        mockMvc.perform(post("/api/v1/cases/bulk-reassign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkReassignBody(List.of(UUID.fromString(caseId)), handlerB.getId())))
                .andExpect(status().isForbidden());

        // The case must be untouched.
        assertThat(caseRepository.findById(UUID.fromString(caseId)).orElseThrow().getAssignedToUser().getId())
                .isEqualTo(handlerA.getId());
    }

    @Test
    void agentToken_isForbidden() throws Exception {
        String caseId = createAssignedCase(handlerA.getId().toString());
        AppUser agent = createUser(UserRole.AGENT);
        String agentToken = login(agent.getUsername());

        mockMvc.perform(post("/api/v1/cases/bulk-reassign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkReassignBody(List.of(UUID.fromString(caseId)), handlerB.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminToken_isAllowed() throws Exception {
        String caseId = createAssignedCase(handlerA.getId().toString());
        AppUser admin = createUser(UserRole.ADMIN);
        String adminToken = login(admin.getUsername());

        bulkReassign(List.of(UUID.fromString(caseId)), handlerB.getId(), adminToken, 200)
                .get("succeeded").asInt();

        assertThat(caseRepository.findById(UUID.fromString(caseId)).orElseThrow().getAssignedToUser().getId())
                .isEqualTo(handlerB.getId());
    }

    // ── AC: destination list = active HANDLER users only ──────────────────

    @Test
    void destinationPicker_listsActiveHandlersOnly() throws Exception {
        AppUser inactiveHandler = createUser(UserRole.HANDLER);
        inactiveHandler.setActive(false);
        userRepository.save(inactiveHandler);

        mockMvc.perform(get("/api/v1/users/handlers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]", handlerA.getId()).exists())
                .andExpect(jsonPath("$[?(@.id == '%s')]", inactiveHandler.getId()).doesNotExist());
    }

    // ── AC: ineligible cases cannot be reassigned; failures reported ──────

    @Test
    void mixedBatch_reassignsEligible_andReportsIneligibleExplicitly() throws Exception {
        String inProgressId = createAssignedCase(handlerA.getId().toString());
        transition(inProgressId, "START", null, supervisorToken);

        String closedId = createAssignedCase(handlerA.getId().toString());
        transition(closedId, "START", null, supervisorToken);
        transition(closedId, "RESOLVE", "\"resolutionSummary\":\"done\"", supervisorToken);
        transition(closedId, "CLOSE", null, supervisorToken);

        JsonNode body = bulkReassign(
                List.of(UUID.fromString(inProgressId), UUID.fromString(closedId)),
                handlerB.getId(), supervisorToken, 200);

        assertThat(body.get("totalRequested").asInt()).isEqualTo(2);
        assertThat(body.get("succeeded").asInt()).isEqualTo(1);
        assertThat(body.get("failed").asInt()).isEqualTo(1);

        JsonNode results = body.get("results");
        JsonNode ok = results.get(0);
        assertThat(ok.get("caseId").asText()).isEqualTo(inProgressId);
        assertThat(ok.get("success").asBoolean()).isTrue();
        JsonNode bad = results.get(1);
        assertThat(bad.get("caseId").asText()).isEqualTo(closedId);
        assertThat(bad.get("success").asBoolean()).isFalse();
        assertThat(bad.get("errorCode").asText()).isEqualTo("INVALID_TRANSITION");
        assertThat(bad.get("message").asText()).contains("CLOSED");

        // Eligible case moved; ineligible case untouched.
        assertThat(caseRepository.findById(UUID.fromString(inProgressId)).orElseThrow().getAssignedToUser().getId())
                .isEqualTo(handlerB.getId());
        assertThat(caseRepository.findById(UUID.fromString(closedId)).orElseThrow().getAssignedToUser().getId())
                .isEqualTo(handlerA.getId());
    }

    @Test
    void unknownCaseId_isReportedAsNotFound() throws Exception {
        String caseId = createAssignedCase(handlerA.getId().toString());

        JsonNode body = bulkReassign(
                List.of(UUID.fromString(caseId), UUID.randomUUID()),
                handlerB.getId(), supervisorToken, 200);

        assertThat(body.get("succeeded").asInt()).isEqualTo(1);
        assertThat(body.get("failed").asInt()).isEqualTo(1);
        JsonNode bad = body.get("results").get(1);
        assertThat(bad.get("success").asBoolean()).isFalse();
        assertThat(bad.get("errorCode").asText()).isEqualTo("NOT_FOUND");
    }

    // ── AC: audit/timeline entry with prev assignee, new assignee, actor ──

    @Test
    void timelineRecordsReassignWithPreviousAndNewAssigneeAndActor() throws Exception {
        String caseId = createAssignedCase(handlerA.getId().toString());

        JsonNode body = bulkReassign(
                List.of(UUID.fromString(caseId)), handlerB.getId(), supervisorToken, 200);
        assertThat(body.get("succeeded").asInt()).isEqualTo(1);

        Case reassigned = caseRepository.findById(UUID.fromString(caseId)).orElseThrow();
        assertThat(reassigned.getAssignedToUser().getId()).isEqualTo(handlerB.getId());
        assertThat(reassigned.getStatus().name()).isEqualTo("ASSIGNED");

        mockMvc.perform(get("/api/v1/cases/{id}/timeline", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))   // CREATE + ASSIGN + REASSIGN
                .andExpect(jsonPath("$[2].action").value("REASSIGN"))
                .andExpect(jsonPath("$[2].fromStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$[2].toStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$[2].changedByDisplayName").value(supervisor.getDisplayName()))
                .andExpect(jsonPath("$[2].createdAt").isNotEmpty());

        var entries = statusHistoryRepository.findByCaseIdOrderByCreatedAtAsc(reassigned.getId());
        assertThat(entries).hasSize(3);
        var reassignEntry = entries.get(2);
        assertThat(reassignEntry.getComment())
                .contains(handlerA.getDisplayName())
                .contains(handlerB.getDisplayName())
                .contains("balancing queue load");
    }

    // ── AC: destination must be an active HANDLER ─────────────────────────

    @Test
    void nonHandlerDestination_isRejected() throws Exception {
        String caseId = createAssignedCase(handlerA.getId().toString());
        AppUser agent = createUser(UserRole.AGENT);

        mockMvc.perform(post("/api/v1/cases/bulk-reassign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkReassignBody(List.of(UUID.fromString(caseId)), agent.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_REASSIGNMENT"));

        assertThat(caseRepository.findById(UUID.fromString(caseId)).orElseThrow().getAssignedToUser().getId())
                .isEqualTo(handlerA.getId());
    }

    @Test
    void inactiveDestination_isRejected() throws Exception {
        String caseId = createAssignedCase(handlerA.getId().toString());
        handlerB.setActive(false);
        userRepository.save(handlerB);

        mockMvc.perform(post("/api/v1/cases/bulk-reassign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkReassignBody(List.of(UUID.fromString(caseId)), handlerB.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Cannot assign case to an inactive user account"));

        assertThat(caseRepository.findById(UUID.fromString(caseId)).orElseThrow().getAssignedToUser().getId())
                .isEqualTo(handlerA.getId());
    }
}
