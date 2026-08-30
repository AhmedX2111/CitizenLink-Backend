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

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-50 "Filter my inbox by urgency" + US-51 "Prioritize work by due date and
 * priority" — end-to-end coverage of the extended
 * GET /api/v1/dashboard/my-inbox (overdue / dueToday / sort params) and the
 * new GET /api/v1/dashboard/my-inbox/counts over the real HTTP surface, in
 * the same self-contained style as InboxIntegrationTest (US-49).
 *
 * Time-zone determinism: every dueAt is derived from OffsetDateTime.now(...)
 * with relative offsets — never fixed calendar dates. "Today" windows are
 * recomputed per call in the zone the server resolves from app.time-zone
 * (blank in the test profile, so the JVM default zone — the same fallback
 * CaseNumberServiceImpl and DashboardServiceImpl use).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InboxUrgencyIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";
    private static final String INBOX_URL = "/api/v1/dashboard/my-inbox";
    private static final String COUNTS_URL = "/api/v1/dashboard/my-inbox/counts";

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

    /** Creates a case via POST /api/v1/cases as the supervisor. */
    private JsonNode createCase(String subject, String priority, String dueAt, AppUser assignedTo) throws Exception {
        StringBuilder body = new StringBuilder()
                .append("{\"subject\":\"").append(subject).append("\",")
                .append("\"description\":\"Created by inbox urgency integration test\",")
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

    /**
     * ASSIGNED -> START -> RESOLVE -> CLOSE chain. The case is created with a
     * past dueAt so it is simultaneously overdue AND closed — the composition
     * fixture for the default final-state exclusion under overdue=true.
     */
    private String createClosedOverdueCase(String subject, String priority, String dueAt) throws Exception {
        JsonNode created = createCase(subject, priority, dueAt, handler);
        String id = created.get("id").asText();
        String handlerToken = login(handler.getUsername());
        transition(handlerToken, id, "START", null);
        transition(handlerToken, id, "RESOLVE", "\"resolutionSummary\":\"Resolved for urgency test\"");
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

    private JsonNode getCounts(String token) throws Exception {
        MvcResult result = mockMvc.perform(get(COUNTS_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<String> contentIds(JsonNode inboxBody) {
        List<String> ids = new ArrayList<>();
        inboxBody.get("content").forEach(row -> ids.add(row.get("id").asText()));
        return ids;
    }

    // ---------------------------------------------------------------------
    // Time-window helpers — all relative to now in the app zone (never fixed
    // calendar dates), recomputed per call so a test run straddling midnight
    // still sees a consistent "today".
    // ---------------------------------------------------------------------

    /** The zone the server resolves from app.time-zone (blank -> JVM default). */
    private static ZoneId appZone() {
        return ZoneId.systemDefault();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(appZone());
    }

    /** todayStart for the server's current calendar day. */
    private static OffsetDateTime todayStart() {
        return LocalDate.now(appZone()).atStartOfDay(appZone()).toOffsetDateTime();
    }

    /** todayEnd = start of the next calendar day (exclusive window bound). */
    private static OffsetDateTime todayEnd() {
        return todayStart().plusDays(1);
    }

    /**
     * A moment guaranteed inside today's window even if the test runs seconds
     * before midnight: the midpoint between now and todayEnd can never roll
     * over into tomorrow.
     */
    private static OffsetDateTime laterToday() {
        OffsetDateTime now = now();
        return now.plus(Duration.between(now, todayEnd()).dividedBy(2));
    }

    /** A moment guaranteed in the past AND outside today's window (yesterday). */
    private static OffsetDateTime yesterday() {
        return todayStart().minusSeconds(3600);
    }

    // US-50: (AC1) overdue=true returns only cases with dueAt != null AND
    // dueAt < now — a future-due case and an undated case are excluded.
    @Test
    void overdue_filter_returns_only_past_due_cases() throws Exception {
        String handlerToken = login(handler.getUsername());

        JsonNode pastDue = createCase("Overdue filter past", "MEDIUM", now().minusHours(1).toString(), handler);
        JsonNode futureDue = createCase("Overdue filter future", "MEDIUM", now().plusDays(2).toString(), handler);
        JsonNode noDue = createCase("Overdue filter undated", "MEDIUM", null, handler);

        JsonNode filtered = getInbox(handlerToken, Map.of("overdue", "true"));
        assertThat(contentIds(filtered)).containsExactly(pastDue.get("id").asText());
        assertThat(filtered.get("totalElements").asLong()).isEqualTo(1);

        // Sanity: without the filter all three are live inbox rows.
        JsonNode all = getInbox(handlerToken, Map.of());
        assertThat(contentIds(all)).containsExactlyInAnyOrder(
                pastDue.get("id").asText(), futureDue.get("id").asText(), noDue.get("id").asText());
    }

    // US-50: (AC1) dueToday=true returns only cases whose dueAt falls inside
    // the current calendar day in the app zone; tomorrow and yesterday are out.
    // All dueAts are derived from now-based windows, never fixed clock times.
    @Test
    void dueToday_filter_returns_cases_due_today() throws Exception {
        String handlerToken = login(handler.getUsername());

        JsonNode dueLaterToday = createCase("Due today case", "MEDIUM", laterToday().toString(), handler);
        JsonNode dueTomorrow = createCase("Due tomorrow case", "MEDIUM", todayEnd().plusHours(1).toString(), handler);
        JsonNode dueYesterday = createCase("Due yesterday case", "MEDIUM", yesterday().toString(), handler);

        JsonNode filtered = getInbox(handlerToken, Map.of("dueToday", "true"));
        assertThat(contentIds(filtered)).containsExactly(dueLaterToday.get("id").asText());
        assertThat(filtered.get("totalElements").asLong()).isEqualTo(1);
    }

    // US-50: (AC2/AC3) filters combine as ANDed predicates: overdue + an
    // explicit priority narrows to the overlap; overdue + dueToday matches
    // nothing here (the fixture's due dates are cleanly separated: strictly
    // before today, or inside today but not yet past — see the javadoc note
    // on cases due earlier today, which satisfy both formulas). The default
    // final-state exclusion also composes with the new dimensions: a CLOSED
    // overdue case stays out of overdue=true, and only an explicit
    // status=CLOSED overrides that default.
    @Test
    void combined_filters_and_semantics() throws Exception {
        String handlerToken = login(handler.getUsername());

        JsonNode overdueUrgent = createCase("Combined overdue urgent", "URGENT", yesterday().toString(), handler);
        JsonNode overdueLow = createCase("Combined overdue low", "LOW", yesterday().toString(), handler);
        JsonNode futureUrgent = createCase("Combined future urgent", "URGENT", now().plusDays(2).toString(), handler);
        String closedOverdueId = createClosedOverdueCase("Combined closed overdue", "HIGH", yesterday().toString());

        // overdue + priority=URGENT -> only the case that is both.
        JsonNode overdueAndUrgent = getInbox(handlerToken, Map.of("overdue", "true", "priority", "URGENT"));
        assertThat(contentIds(overdueAndUrgent)).containsExactly(overdueUrgent.get("id").asText());

        // overdue + dueToday -> empty by definition (a case cannot be both).
        JsonNode overdueAndDueToday = getInbox(handlerToken, Map.of("overdue", "true", "dueToday", "true"));
        assertThat(contentIds(overdueAndDueToday)).isEmpty();
        assertThat(overdueAndDueToday.get("totalElements").asLong()).isZero();

        // Default final-state exclusion composes with overdue: the CLOSED
        // overdue case is absent even though it is past due...
        JsonNode overdueOnly = getInbox(handlerToken, Map.of("overdue", "true"));
        assertThat(contentIds(overdueOnly)).containsExactlyInAnyOrder(
                overdueUrgent.get("id").asText(), overdueLow.get("id").asText());
        assertThat(contentIds(overdueOnly)).doesNotContain(closedOverdueId);

        // ...and only an explicit status=CLOSED override surfaces it again.
        JsonNode closedOverdue = getInbox(handlerToken, Map.of("status", "CLOSED", "overdue", "true"));
        assertThat(contentIds(closedOverdue)).containsExactly(closedOverdueId);
    }

    // US-50: (AC2) counts are computed over the caller's permitted cases with
    // the default final-state exclusion on every dimension; dimensions overlap
    // (an URGENT case due later today counts under all, dueToday, urgent and
    // newlyAssigned), and a second handler's workload never leaks in.
    @Test
    void counts_endpoint_reports_quick_filter_totals_scoped_to_handler() throws Exception {
        String handlerToken = login(handler.getUsername());

        // c1: overdue (yesterday) + LOW + ASSIGNED          -> all, overdue, newlyAssigned
        createCase("Counts overdue", "LOW", yesterday().toString(), handler);
        // c2: due later today + MEDIUM + ASSIGNED           -> all, dueToday, newlyAssigned
        createCase("Counts due today", "MEDIUM", laterToday().toString(), handler);
        // c3: future due + URGENT + ASSIGNED                -> all, urgent, newlyAssigned
        createCase("Counts urgent", "URGENT", now().plusDays(2).toString(), handler);
        // c4: undated + MEDIUM + AWAITING_INFO              -> all, awaitingInfo
        JsonNode c4 = createCase("Counts awaiting info", "MEDIUM", null, handler);
        String c4Id = c4.get("id").asText();
        transition(handlerToken, c4Id, "START", null);
        transition(handlerToken, c4Id, "AWAIT_INFO", null);
        // c5: future due + HIGH + ASSIGNED                  -> all, newlyAssigned
        createCase("Counts newly assigned", "HIGH", now().plusDays(3).toString(), handler);
        // closed overdue case: must count NOWHERE (final-state exclusion).
        createClosedOverdueCase("Counts closed overdue", "HIGH", yesterday().toString());

        JsonNode counts = getCounts(handlerToken);
        assertThat(counts.get("all").asLong()).isEqualTo(5);
        assertThat(counts.get("overdue").asLong()).isEqualTo(1);
        assertThat(counts.get("dueToday").asLong()).isEqualTo(1);
        assertThat(counts.get("urgent").asLong()).isEqualTo(1);
        assertThat(counts.get("awaitingInfo").asLong()).isEqualTo(1);
        assertThat(counts.get("newlyAssigned").asLong()).isEqualTo(4);

        // Scoping: another handler's case counts only for that handler.
        createCase("Counts other handler", "MEDIUM", now().plusDays(2).toString(), otherHandler);

        JsonNode countsAfterOther = getCounts(handlerToken);
        assertThat(countsAfterOther.get("all").asLong()).isEqualTo(5);
        assertThat(countsAfterOther.get("newlyAssigned").asLong()).isEqualTo(4);

        String otherToken = login(otherHandler.getUsername());
        JsonNode otherCounts = getCounts(otherToken);
        assertThat(otherCounts.get("all").asLong()).isEqualTo(1);
        assertThat(otherCounts.get("overdue").asLong()).isZero();
        assertThat(otherCounts.get("dueToday").asLong()).isZero();
        assertThat(otherCounts.get("urgent").asLong()).isZero();
        assertThat(otherCounts.get("awaitingInfo").asLong()).isZero();
        assertThat(otherCounts.get("newlyAssigned").asLong()).isEqualTo(1);

        // Sanity: the six numbers really match the corresponding quick filters.
        assertThat(getInbox(handlerToken, Map.of("overdue", "true")).get("totalElements").asLong())
                .isEqualTo(counts.get("overdue").asLong());
        assertThat(getInbox(handlerToken, Map.of("dueToday", "true")).get("totalElements").asLong())
                .isEqualTo(counts.get("dueToday").asLong());
        assertThat(getInbox(handlerToken, Map.of("priority", "URGENT")).get("totalElements").asLong())
                .isEqualTo(counts.get("urgent").asLong());
        assertThat(getInbox(handlerToken, Map.of("status", "AWAITING_INFO")).get("totalElements").asLong())
                .isEqualTo(counts.get("awaitingInfo").asLong());
        assertThat(getInbox(handlerToken, Map.of("status", "ASSIGNED")).get("totalElements").asLong())
                .isEqualTo(counts.get("newlyAssigned").asLong());
    }

    // US-51: (AC1) SMART places an overdue LOW-priority case before a future
    // URGENT case, and the future URGENT before a future undated case — the
    // overdue state dominates, then priority rank, then due date (nulls last).
    @Test
    void smart_sort_overdue_first() throws Exception {
        String handlerToken = login(handler.getUsername());

        JsonNode overdueLow = createCase("Smart overdue low", "LOW", now().minusHours(1).toString(), handler);
        JsonNode futureUrgent = createCase("Smart future urgent", "URGENT", now().plusDays(2).toString(), handler);
        JsonNode futureUndated = createCase("Smart future undated", "MEDIUM", null, handler);

        JsonNode body = getInbox(handlerToken, Map.of()); // default sort = SMART
        assertThat(contentIds(body)).containsExactly(
                overdueLow.get("id").asText(),
                futureUrgent.get("id").asText(),
                futureUndated.get("id").asText());
    }

    // US-51: (AC1) within the same due state (non-overdue) and the same due
    // date, SMART ranks by priority: URGENT before MEDIUM, exact order.
    @Test
    void smart_sort_priority_within_same_due_state() throws Exception {
        String handlerToken = login(handler.getUsername());

        String sameDue = now().plusDays(3).toString();
        JsonNode medium = createCase("Smart same-due medium", "MEDIUM", sameDue, handler);
        JsonNode urgent = createCase("Smart same-due urgent", "URGENT", sameDue, handler);

        JsonNode body = getInbox(handlerToken, Map.of());
        assertThat(contentIds(body)).containsExactly(
                urgent.get("id").asText(),
                medium.get("id").asText());
    }

    // US-51: (AC2) DUE_DATE reproduces the US-49 ordering — dueAt ascending
    // with undated cases LAST.
    @Test
    void sort_due_date_orders_by_due_at_nulls_last() throws Exception {
        String handlerToken = login(handler.getUsername());

        JsonNode earliest = createCase("Due date earliest", "MEDIUM", now().plusDays(1).toString(), handler);
        JsonNode later = createCase("Due date later", "MEDIUM", now().plusDays(2).toString(), handler);
        JsonNode undated = createCase("Due date undated", "MEDIUM", null, handler);

        JsonNode body = getInbox(handlerToken, Map.of("sort", "DUE_DATE"));
        assertThat(contentIds(body)).containsExactly(
                earliest.get("id").asText(),
                later.get("id").asText(),
                undated.get("id").asText());
    }

    // US-51: (AC2) PRIORITY ranks by URGENT > HIGH > MEDIUM > LOW, then by
    // due date for equal ranks.
    @Test
    void sort_priority_orders_by_rank_then_due_date() throws Exception {
        String handlerToken = login(handler.getUsername());

        JsonNode low = createCase("Priority sort low", "LOW", now().plusDays(2).toString(), handler);
        JsonNode high = createCase("Priority sort high", "HIGH", now().plusDays(1).toString(), handler);
        JsonNode medium = createCase("Priority sort medium", "MEDIUM", now().plusDays(2).toString(), handler);

        JsonNode body = getInbox(handlerToken, Map.of("sort", "PRIORITY"));
        assertThat(contentIds(body)).containsExactly(
                high.get("id").asText(),
                medium.get("id").asText(),
                low.get("id").asText());
    }

    // US-51: (AC2) NEWEST orders by updatedAt DESC — the most recently
    // created (and therefore updated) case comes first.
    @Test
    void sort_newest_orders_by_last_update_desc() throws Exception {
        String handlerToken = login(handler.getUsername());

        JsonNode first = createCase("Newest first", "MEDIUM", null, handler);
        JsonNode second = createCase("Newest second", "MEDIUM", null, handler);
        JsonNode third = createCase("Newest third", "MEDIUM", null, handler);

        JsonNode body = getInbox(handlerToken, Map.of("sort", "NEWEST"));
        assertThat(contentIds(body)).containsExactly(
                third.get("id").asText(),
                second.get("id").asText(),
                first.get("id").asText());
    }

    // US-51: (AC3) sorting happens server-side and composes with pagination —
    // page 2 continues the global SMART order across the page boundary.
    @Test
    void smart_sort_works_with_pagination() throws Exception {
        String handlerToken = login(handler.getUsername());

        JsonNode p1Overdue = createCase("Paging overdue", "LOW", now().minusHours(1).toString(), handler);
        JsonNode p2Urgent = createCase("Paging urgent", "URGENT", now().plusDays(1).toString(), handler);
        JsonNode p3High = createCase("Paging high", "HIGH", now().plusDays(2).toString(), handler);
        JsonNode p4Medium = createCase("Paging medium", "MEDIUM", now().plusDays(3).toString(), handler);

        // Global SMART order: overdue first, then priority rank.
        JsonNode page0 = getInbox(handlerToken, Map.of("page", "0", "size", "2"));
        assertThat(page0.get("totalElements").asLong()).isEqualTo(4);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(contentIds(page0)).containsExactly(
                p1Overdue.get("id").asText(), p2Urgent.get("id").asText());

        JsonNode page1 = getInbox(handlerToken, Map.of("page", "1", "size", "2"));
        assertThat(page1.get("first").asBoolean()).isFalse();
        assertThat(page1.get("last").asBoolean()).isTrue();
        assertThat(contentIds(page1)).containsExactly(
                p3High.get("id").asText(), p4Medium.get("id").asText());

        // The two pages together reproduce the un-paginated SMART order.
        List<String> paginated = new ArrayList<>(contentIds(page0));
        paginated.addAll(contentIds(page1));
        assertThat(paginated).isEqualTo(contentIds(getInbox(handlerToken, Map.of())));
    }

    // US-50: (AC1) the counts endpoint is HANDLER-only like the inbox itself —
    // every other role is rejected and anonymous callers get 401.
    @Test
    void counts_requires_handler_role() throws Exception {
        String agentToken = login(createUser(UserRole.AGENT).getUsername());
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());

        mockMvc.perform(get(COUNTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(COUNTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(COUNTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(COUNTS_URL))
                .andExpect(status().isUnauthorized());
    }

    // US-50: (AC3 contract preservation) the new params stay optional — an
    // oversized size is still rejected exactly as before the extension
    // (1 <= size <= 100, same validation as US-49).
    @Test
    void inbox_params_validation_unchanged_after_extension() throws Exception {
        String handlerToken = login(handler.getUsername());

        mockMvc.perform(get(INBOX_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken)
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest());

        // The new params are all optional and round-trip as plain URL params.
        mockMvc.perform(get(INBOX_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken)
                        .queryParam("overdue", "true")
                        .queryParam("dueToday", "true")
                        .queryParam("sort", "SMART"))
                .andExpect(status().isOk());
    }
}
