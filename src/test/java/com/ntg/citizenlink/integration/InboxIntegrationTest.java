package com.ntg.citizenlink.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Category;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.entities.Department;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.CategoryRepository;
import com.ntg.citizenlink.repositories.CitizenRepository;
import com.ntg.citizenlink.repositories.DepartmentRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-49 "View my work inbox" — end-to-end coverage of the
 * GET /api/v1/dashboard/my-inbox endpoint over the real HTTP surface:
 * real login, EntityFactory fixtures, workflow transitions through
 * POST /api/v1/cases/{id}/transition.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InboxIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";
    private static final String INBOX_URL = "/api/v1/dashboard/my-inbox";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppUser supervisor;
    private AppUser handler;
    private AppUser otherHandler;
    private Citizen citizen;
    private Category category;
    private Department department;
    private String supervisorToken;

    @BeforeEach
    void setUp() throws Exception {
        supervisor = createUser(UserRole.SUPERVISOR);
        handler = createUser(UserRole.HANDLER);
        otherHandler = createUser(UserRole.HANDLER);
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

    /**
     * Creates a case via POST /api/v1/cases as the supervisor. With an assignee
     * the case is created directly in ASSIGNED (pre-assigned creation), without
     * one it stays NEW and unassigned. Returns the full create response.
     */
    private JsonNode createCase(String subject, String priority, String dueAt, AppUser assignedTo) throws Exception {
        StringBuilder body = new StringBuilder()
                .append("{\"subject\":\"").append(subject).append("\",")
                .append("\"description\":\"Created by inbox integration test\",")
                .append("\"type\":\"COMPLAINT\",")
                .append("\"priority\":\"").append(priority).append("\",")
                .append("\"channel\":\"WEB\",")
                .append("\"citizenNationalId\":\"").append(citizen.getNationalId()).append("\",")
                .append("\"categoryId\":\"").append(category.getId()).append("\",")
                .append("\"departmentId\":\"").append(department.getId()).append("\"");
        if (assignedTo != null) {
            body.append(",\"assignedToUserId\":\"").append(assignedTo.getId()).append("\"");
        }
        if (dueAt != null) {
            body.append(",\"dueAt\":\"").append(dueAt).append("\"");
        }
        body.append("}");

        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

    /** ASSIGN -> START -> RESOLVE -> CLOSE chain (all reachable via API transitions). */
    private String createClosedCase(String subject) throws Exception {
        JsonNode created = createCase(subject, "HIGH", null, handler);
        String id = created.get("id").asText();
        String handlerToken = login(handler.getUsername());
        transition(handlerToken, id, "START", null);
        transition(handlerToken, id, "RESOLVE", "\"resolutionSummary\":\"Resolved for inbox test\"");
        transition(supervisorToken, id, "CLOSE", null);
        return id;
    }

    private JsonNode getInbox(String token, Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder request = get(INBOX_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        params.forEach(request::queryParam);
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<String> contentIds(JsonNode inboxBody) {
        List<String> ids = new ArrayList<>();
        inboxBody.get("content").forEach(row -> ids.add(row.get("id").asText()));
        return ids;
    }

    // US-49: (AC1) the inbox contains only cases assigned to the logged-in handler.
    @Test
    void handler_sees_only_cases_assigned_to_them() throws Exception {
        String handlerToken = login(handler.getUsername());

        JsonNode mine = createCase("Inbox ownership mine", "HIGH", null, handler);
        JsonNode theirs = createCase("Inbox ownership theirs", "HIGH", null, otherHandler);
        JsonNode unassigned = createCase("Inbox ownership unassigned", "HIGH", null, null);

        JsonNode body = getInbox(handlerToken, Map.of());

        assertThat(contentIds(body)).contains(mine.get("id").asText());
        assertThat(contentIds(body))
                .doesNotContain(theirs.get("id").asText(), unassigned.get("id").asText());
    }

    // US-49: (AC2) CLOSED and CANCELLED (final states) are excluded by default;
    // the still-open assigned case is the only row returned.
    @Test
    void inbox_excludes_final_states_by_default() throws Exception {
        String handlerToken = login(handler.getUsername());

        String closedId = createClosedCase("Inbox default exclusion closed");

        JsonNode cancelled = createCase("Inbox default exclusion cancelled", "HIGH", null, handler);
        transition(supervisorToken, cancelled.get("id").asText(), "CANCEL", null);

        JsonNode open = createCase("Inbox default exclusion open", "HIGH", null, handler);

        JsonNode body = getInbox(handlerToken, Map.of());

        assertThat(contentIds(body)).containsExactly(open.get("id").asText());
        assertThat(contentIds(body))
                .doesNotContain(closedId, cancelled.get("id").asText());
    }

    // US-49: (AC2) AWAITING_INFO and SUSPENDED are non-final and MUST appear in
    // the default inbox — unlike the US-06 top-5 widget which drops them.
    @Test
    void inbox_includes_awaitingInfo_and_suspended() throws Exception {
        String handlerToken = login(handler.getUsername());

        JsonNode awaiting = createCase("Inbox awaiting info case", "MEDIUM", null, handler);
        String awaitingId = awaiting.get("id").asText();
        transition(handlerToken, awaitingId, "START", null);
        transition(handlerToken, awaitingId, "AWAIT_INFO", null);

        JsonNode suspended = createCase("Inbox suspended case", "MEDIUM", null, handler);
        String suspendedId = suspended.get("id").asText();
        transition(handlerToken, suspendedId, "START", null);
        transition(handlerToken, suspendedId, "SUSPEND", "\"comment\":\"Waiting for citizen documents\"");

        JsonNode body = getInbox(handlerToken, Map.of());

        assertThat(contentIds(body)).containsExactlyInAnyOrder(awaitingId, suspendedId);
    }

    // US-49: (AC2 documented override) an explicit status filter replaces the
    // default final-state exclusion — a CLOSED case is returned for status=CLOSED.
    @Test
    void inbox_explicit_status_filter_overrides_default() throws Exception {
        String handlerToken = login(handler.getUsername());

        String closedId = createClosedCase("Inbox explicit status closed");

        JsonNode byStatus = getInbox(handlerToken, Map.of("status", "CLOSED"));

        assertThat(contentIds(byStatus)).containsExactly(closedId);
        byStatus.get("content")
                .forEach(row -> assertThat(row.get("status").asText()).isEqualTo("CLOSED"));

        // Sanity: without the explicit status the same case is excluded again.
        assertThat(contentIds(getInbox(handlerToken, Map.of()))).doesNotContain(closedId);
    }

    // US-49: (AC4) priority and keyword filters. Priority exact-matches;
    // keyword matches case-number prefix OR subject fragment, and wildcard
    // characters (% _) in the keyword are matched literally (mirrors the
    // keywordSearch_treatsPercent/UnderscoreAsLiteral tests of CaseRepositoryTest).
    @Test
    void inbox_priority_and_keyword_filters() throws Exception {
        String handlerToken = login(handler.getUsername());

        JsonNode urgent = createCase("Inbox filter urgent", "URGENT", null, handler);
        JsonNode low = createCase("Inbox filter low", "LOW", null, handler);
        JsonNode percent = createCase("Discount 50% OFF", "MEDIUM", null, handler);
        createCase("Discount 500 OFF", "MEDIUM", null, handler);      // % wildcard decoy
        JsonNode underscore = createCase("report_2026", "MEDIUM", null, handler);
        createCase("reportX2026", "MEDIUM", null, handler);           // _ wildcard decoy

        // Priority filter returns only the matching case.
        JsonNode byPriority = getInbox(handlerToken, Map.of("priority", "URGENT"));
        assertThat(contentIds(byPriority)).containsExactly(urgent.get("id").asText());

        // Keyword by case-number prefix: prefix = full number minus last digit.
        String caseNumber = urgent.get("caseNumber").asText();
        String prefix = caseNumber.substring(0, caseNumber.length() - 1);
        JsonNode byPrefix = getInbox(handlerToken, Map.of("keyword", prefix));
        assertThat(contentIds(byPrefix)).contains(urgent.get("id").asText());
        byPrefix.get("content")
                .forEach(row -> assertThat(row.get("caseNumber").asText()).startsWith(prefix));

        // Keyword by exact case number resolves to exactly that case.
        JsonNode byCaseNumber = getInbox(handlerToken, Map.of("keyword", caseNumber));
        assertThat(contentIds(byCaseNumber)).containsExactly(urgent.get("id").asText());

        // Keyword by subject fragment (case-insensitive LIKE).
        JsonNode bySubject = getInbox(handlerToken, Map.of("keyword", "FILTER URGENT"));
        assertThat(contentIds(bySubject)).containsExactly(urgent.get("id").asText());

        // A % in the keyword is a literal percent, not a wildcard.
        JsonNode byPercent = getInbox(handlerToken, Map.of("keyword", "50%"));
        assertThat(contentIds(byPercent)).containsExactly(percent.get("id").asText());

        // A _ in the keyword is a literal underscore, not a single-char wildcard.
        JsonNode byUnderscore = getInbox(handlerToken, Map.of("keyword", "report_"));
        assertThat(contentIds(byUnderscore)).containsExactly(underscore.get("id").asText());
    }

    // US-49: (AC4) server-side pagination with dueAt ASC nulls-last ordering
    // and id ASC tiebreaker for stable pagination.
    @Test
    void inbox_is_paginated_server_side() throws Exception {
        String handlerToken = login(handler.getUsername());

        String dueA = "2030-01-01T10:00:00Z";
        String dueB = "2030-01-02T10:00:00Z";
        String dueTie = "2030-01-03T10:00:00Z";
        String dueC = "2030-01-04T10:00:00Z";

        createCase("Inbox paging A", "MEDIUM", dueA, handler);
        createCase("Inbox paging B", "MEDIUM", dueB, handler);
        JsonNode tie1 = createCase("Inbox paging tie1", "MEDIUM", dueTie, handler);
        JsonNode tie2 = createCase("Inbox paging tie2", "MEDIUM", dueTie, handler);
        createCase("Inbox paging C", "MEDIUM", dueC, handler);
        createCase("Inbox paging null1", "MEDIUM", null, handler);
        createCase("Inbox paging null2", "MEDIUM", null, handler);

        JsonNode page0 = getInbox(handlerToken, Map.of("page", "0", "size", "5"));

        assertThat(page0.get("totalElements").asLong()).isEqualTo(7);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.get("page").asInt()).isZero();
        assertThat(page0.get("size").asInt()).isEqualTo(5);
        assertThat(page0.get("first").asBoolean()).isTrue();
        assertThat(page0.get("last").asBoolean()).isFalse();
        assertThat(page0.get("content").size()).isEqualTo(5);

        JsonNode rows0 = page0.get("content");
        List<OffsetDateTime> dueAts = new ArrayList<>();
        for (JsonNode row : rows0) {
            assertThat(row.get("dueAt").isNull()).isFalse();
            dueAts.add(OffsetDateTime.parse(row.get("dueAt").asText()));
        }
        // Most urgent first: A, B, then the tie pair, then C.
        assertThat(dueAts.get(0).toInstant()).isEqualTo(OffsetDateTime.parse(dueA).toInstant());
        assertThat(dueAts.get(1).toInstant()).isEqualTo(OffsetDateTime.parse(dueB).toInstant());
        assertThat(dueAts.get(2).toInstant()).isEqualTo(OffsetDateTime.parse(dueTie).toInstant());
        assertThat(dueAts.get(3).toInstant()).isEqualTo(OffsetDateTime.parse(dueTie).toInstant());
        assertThat(dueAts.get(4).toInstant()).isEqualTo(OffsetDateTime.parse(dueC).toInstant());

        // Tied due dates are broken by id ASC so pagination stays stable:
        // the tie pair is adjacent, and a repeated request returns the exact
        // same id sequence (the DB orders the uuid column byte-wise, which is
        // not Java's UUID.compareTo — determinism is the contract under test).
        UUID firstTieId = UUID.fromString(rows0.get(2).get("id").asText());
        UUID secondTieId = UUID.fromString(rows0.get(3).get("id").asText());
        assertThat(Set.of(firstTieId.toString(), secondTieId.toString()))
                .isEqualTo(Set.of(tie1.get("id").asText(), tie2.get("id").asText()));

        List<String> page0Ids = contentIds(page0);
        assertThat(page0Ids).isEqualTo(contentIds(getInbox(handlerToken, Map.of("page", "0", "size", "5"))));

        // Page 1 holds the two null-dueAt cases (nulls last) and is the last page.
        JsonNode page1 = getInbox(handlerToken, Map.of("page", "1", "size", "5"));
        assertThat(page1.get("content").size()).isEqualTo(2);
        assertThat(page1.get("first").asBoolean()).isFalse();
        assertThat(page1.get("last").asBoolean()).isTrue();
        page1.get("content")
                .forEach(row -> assertThat(row.get("dueAt").isNull()).isTrue());
    }

    // US-49: (AC3) the inbox row exposes all columns the screen renders:
    // id, caseNumber, subject, citizenFullName, priority, status, dueAt, updatedAt.
    @Test
    void inbox_columns_present() throws Exception {
        String handlerToken = login(handler.getUsername());

        createCase("Inbox columns subject", "HIGH", "2030-06-01T09:00:00Z", handler);

        JsonNode body = getInbox(handlerToken, Map.of());

        assertThat(body.get("content").size()).isEqualTo(1);
        JsonNode row = body.get("content").get(0);

        assertThat(row.get("id").asText()).isNotBlank();
        assertThat(row.get("caseNumber").asText()).isNotBlank();
        assertThat(row.get("subject").asText()).isEqualTo("Inbox columns subject");

        String citizenFullName = row.get("citizenFullName").asText();
        assertThat(citizenFullName).isNotBlank();
        assertThat(citizenFullName).isEqualTo(citizen.getFullName());

        assertThat(row.get("priority").asText()).isEqualTo("HIGH");
        assertThat(row.get("status").asText()).isEqualTo("ASSIGNED");
        assertThat(row.get("dueAt").isNull()).isFalse();
        assertThat(row.get("updatedAt").isNull()).isFalse();
    }

    // US-49: (AC1) the inbox is HANDLER-only — every other role is rejected
    // and anonymous callers get 401.
    @Test
    void inbox_requires_handler_role() throws Exception {
        String agentToken = login(createUser(UserRole.AGENT).getUsername());
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());

        mockMvc.perform(get(INBOX_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(INBOX_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(INBOX_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(INBOX_URL))
                .andExpect(status().isUnauthorized());
    }
}
