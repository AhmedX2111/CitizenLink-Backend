package com.ntg.citizenlink.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.CitizenRepository;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-54 end-to-end: GET /api/v1/dashboard/workload over the real HTTP
 * surface with seeded data.
 *
 * Covers the acceptance criteria:
 *   - HANDLER sees PERSONAL counts for own assigned/overdue/due-today cases
 *   - SUPERVISOR sees TEAM overdue/due-today/unassigned counts
 *   - each indicator's link (case list / inbox with matching filter)
 *     returns exactly the counted number of rows
 *   - counts respect visibility (a second handler with no cases sees zeros)
 *   - AGENT is rejected (only handler/supervisor may access)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkloadIndicatorsIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";
    private static final String WORKLOAD_URL = "/api/v1/dashboard/workload";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private com.ntg.citizenlink.repositories.CategoryRepository categoryRepository;
    @Autowired private com.ntg.citizenlink.repositories.DepartmentRepository departmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppUser supervisor;
    private AppUser handlerA;
    private AppUser handlerB;
    private Citizen citizen;
    private com.ntg.citizenlink.entities.Category category;
    private com.ntg.citizenlink.entities.Department department;
    private String supervisorToken;
    private String handlerAToken;
    private String handlerBToken;

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
        handlerAToken = login(handlerA.getUsername());
        handlerBToken = login(handlerB.getUsername());
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

    /**
     * Creates a case via POST /api/v1/cases as the supervisor. With an
     * assignee the case is created directly in ASSIGNED, without one it
     * stays NEW and unassigned.
     */
    private String createCase(String subject, OffsetDateTime dueAt, AppUser assignedTo) throws Exception {
        String body = """
                {
                  "subject": "%s",
                  "description": "Created by workload integration test",
                  "type": "COMPLAINT",
                  "priority": "HIGH",
                  "channel": "WEB",
                  "citizenNationalId": "%s",
                  "categoryId": "%s",
                  "departmentId": "%s"%s%s
                }
                """.formatted(
                subject,
                citizen.getNationalId(),
                category.getId(),
                department.getId(),
                assignedTo != null ? ",\"assignedToUserId\": \"" + assignedTo.getId() + "\"" : "",
                dueAt != null ? ",\"dueAt\": \"" + dueAt + "\"" : "");

        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private OffsetDateTime daysFromNow(int days) {
        return OffsetDateTime.now(ZoneId.systemDefault()).plusDays(days);
    }

    /** Due later today but before midnight — counts as due-today, not overdue. */
    private OffsetDateTime dueLaterToday() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        return today.atTime(23, 0).atZone(zone).toOffsetDateTime();
    }

    private JsonNode getWorkload(String token) throws Exception {
        MvcResult result = mockMvc.perform(get(WORKLOAD_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long indicatorCount(JsonNode workload, String key) {
        for (JsonNode indicator : workload.get("indicators")) {
            if (key.equals(indicator.get("key").asText())) {
                return indicator.get("count").asLong();
            }
        }
        throw new AssertionError("Indicator not found: " + key);
    }

    private String indicatorLink(JsonNode workload, String key) {
        for (JsonNode indicator : workload.get("indicators")) {
            if (key.equals(indicator.get("key").asText())) {
                return indicator.get("link").asText();
            }
        }
        throw new AssertionError("Indicator not found: " + key);
    }

    /** Follows an indicator link and returns totalElements of the result page. */
    private long followLink(String link, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(link)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("totalElements").asLong();
    }

    // ── seeded scenario ───────────────────────────────────────────────────
    //
    // handlerA: 1 overdue assigned, 1 due-today assigned, 1 assigned with a
    // future due date, 1 closed overdue (excluded from every count).
    // handlerB: nothing assigned.
    // Queue: 2 unassigned NEW cases.
    //
    // Expected handlerA (PERSONAL): ASSIGNED=3, OVERDUE=1, DUE_TODAY=1
    // Expected handlerB (PERSONAL): all zero
    // Expected supervisor (TEAM):   OVERDUE=1, DUE_TODAY=1, UNASSIGNED=2
    //
    // ──────────────────────────────────────────────────────────────────────

    private void seedScenario() throws Exception {
        createCase("A overdue", daysFromNow(-2), handlerA);          // overdue assigned
        createCase("A due today", dueLaterToday(), handlerA);        // due today assigned
        createCase("A future", daysFromNow(7), handlerA);            // future assigned
        String closed = createCase("A closed", daysFromNow(-3), handlerA);
        closeCase(closed);
        createCase("Queue unassigned 1", null, null);                // NEW, unassigned
        createCase("Queue unassigned 2", null, null);                // NEW, unassigned
    }

    /** START/RESOLVE as the assigned handler, CLOSE as the supervisor. */
    private void closeCase(String caseId) throws Exception {
        transition(handlerAToken, caseId, "START", null);
        transition(handlerAToken, caseId, "RESOLVE", "\"resolutionSummary\":\"done\"");
        transition(supervisorToken, caseId, "CLOSE", null);
    }

    private void transition(String token, String caseId, String action, String extra) throws Exception {
        String body = extra == null || extra.isBlank()
                ? "{\"action\":\"" + action + "\"}"
                : "{\"action\":\"" + action + "\"," + extra + "}";
        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ── tests ─────────────────────────────────────────────────────────────

    @Test
    void handler_seesPersonalCounts_forOwnCases() throws Exception {
        seedScenario();

        JsonNode workload = getWorkload(handlerAToken);

        assertThat(workload.get("scope").asText()).isEqualTo("PERSONAL");
        assertThat(indicatorCount(workload, "ASSIGNED")).isEqualTo(3);
        assertThat(indicatorCount(workload, "OVERDUE")).isEqualTo(1);
        assertThat(indicatorCount(workload, "DUE_TODAY")).isEqualTo(1);
    }

    @Test
    void handlerWithNoCases_seesZeros_visibilityRespected() throws Exception {
        seedScenario();

        JsonNode workload = getWorkload(handlerBToken);

        assertThat(workload.get("scope").asText()).isEqualTo("PERSONAL");
        assertThat(indicatorCount(workload, "ASSIGNED")).isZero();
        assertThat(indicatorCount(workload, "OVERDUE")).isZero();
        assertThat(indicatorCount(workload, "DUE_TODAY")).isZero();
    }

    @Test
    void supervisor_seesTeamCounts() throws Exception {
        // Team counts span the whole DB (which is shared across test classes),
        // so assert the delta caused by this test's seeded data, not absolute
        // values.
        JsonNode before = getWorkload(supervisorToken);
        long overdueBefore = indicatorCount(before, "OVERDUE");
        long dueTodayBefore = indicatorCount(before, "DUE_TODAY");
        long unassignedBefore = indicatorCount(before, "UNASSIGNED");

        seedScenario();

        JsonNode after = getWorkload(supervisorToken);

        assertThat(after.get("scope").asText()).isEqualTo("TEAM");
        assertThat(indicatorCount(after, "OVERDUE") - overdueBefore).isEqualTo(1);
        assertThat(indicatorCount(after, "DUE_TODAY") - dueTodayBefore).isEqualTo(1);
        assertThat(indicatorCount(after, "UNASSIGNED") - unassignedBefore).isEqualTo(2);
    }

    @Test
    void handlerIndicatorLinks_returnExactlyTheCountedRows() throws Exception {
        seedScenario();

        JsonNode workload = getWorkload(handlerAToken);
        long assigned = indicatorCount(workload, "ASSIGNED");
        long overdue = indicatorCount(workload, "OVERDUE");
        long dueToday = indicatorCount(workload, "DUE_TODAY");

        assertThat(followLink(indicatorLink(workload, "ASSIGNED"), handlerAToken)).isEqualTo(assigned);
        assertThat(followLink(indicatorLink(workload, "OVERDUE"), handlerAToken)).isEqualTo(overdue);
        assertThat(followLink(indicatorLink(workload, "DUE_TODAY"), handlerAToken)).isEqualTo(dueToday);
    }

    @Test
    void supervisorIndicatorLinks_returnExactlyTheCountedRows() throws Exception {
        seedScenario();

        JsonNode workload = getWorkload(supervisorToken);

        // The team counts span the whole shared DB; whatever the indicator
        // says, its linked filter must return exactly that many rows.
        assertThat(followLink(indicatorLink(workload, "OVERDUE"), supervisorToken))
                .isEqualTo(indicatorCount(workload, "OVERDUE"));
        assertThat(followLink(indicatorLink(workload, "DUE_TODAY"), supervisorToken))
                .isEqualTo(indicatorCount(workload, "DUE_TODAY"));
        assertThat(followLink(indicatorLink(workload, "UNASSIGNED"), supervisorToken))
                .isEqualTo(indicatorCount(workload, "UNASSIGNED"));
    }

    @Test
    void agent_isForbidden() throws Exception {
        AppUser agent = createUser(UserRole.AGENT);
        String agentToken = login(agent.getUsername());

        mockMvc.perform(get(WORKLOAD_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_getsTeamScope() throws Exception {
        AppUser admin = createUser(UserRole.ADMIN);
        String adminToken = login(admin.getUsername());

        mockMvc.perform(get(WORKLOAD_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        JsonNode workload = getWorkload(adminToken);
        assertThat(workload.get("scope").asText()).isEqualTo("TEAM");
        assertThat(workload.get("indicators").isArray()).isTrue();
        assertThat(workload.get("indicators").size()).isEqualTo(3);
    }

    @Test
    void unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get(WORKLOAD_URL))
                .andExpect(status().isUnauthorized());
    }
}
