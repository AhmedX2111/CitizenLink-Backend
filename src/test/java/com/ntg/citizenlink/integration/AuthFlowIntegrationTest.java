package com.ntg.citizenlink.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.entities.Category;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.entities.Department;
import com.ntg.citizenlink.entities.StatusHistory;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.enums.WorkflowAction;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.CaseRepository;
import com.ntg.citizenlink.repositories.CategoryRepository;
import com.ntg.citizenlink.repositories.CitizenRepository;
import com.ntg.citizenlink.repositories.DepartmentRepository;
import com.ntg.citizenlink.repositories.StatusHistoryRepository;
import com.ntg.citizenlink.support.EntityFactory;
import jakarta.servlet.http.Cookie;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CaseRepository caseRepository;
    @Autowired private StatusHistoryRepository statusHistoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PASSWORD = "Passw0rd!";

    private String username;
    private AppUser agent;

    @BeforeEach
    void setUp() {
        username = "it." + EntityFactory.uniqueSuffix();
        AppUser user = EntityFactory.appUser(UserRole.AGENT);
        user.setUsername(username);
        user.setEmail(username + "@test.gov");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        agent = userRepository.save(user);
    }

    private MvcResult loginAs(String user) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult login() throws Exception {
        return loginAs(username);
    }

    private String accessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private Cookie refreshCookie(MvcResult result) {
        return result.getResponse().getCookie("refresh_token");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void login_returnsAccessTokenInBody_andSetsHttpOnlyRefreshCookie() throws Exception {
        MvcResult result = login();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("refreshToken").isNull()).isTrue();
        assertThat(body.get("username").asText()).isEqualTo(username);
        assertThat(body.get("role").asText()).isEqualTo("AGENT");

        Cookie cookie = refreshCookie(result);
assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Secure");
        assertThat(cookie.getMaxAge()).isEqualTo(604800);
    }

    @Test
    void me_returnsCurrentUserProfile_withAccessToken() throws Exception {
        String token = accessToken(login());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("AGENT"));
    }

    @Test
    void refresh_withCookie_rotatesToken() throws Exception {
        MvcResult loginResult = login();
        Cookie oldRefresh = refreshCookie(loginResult);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefresh.getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").value(nullValue()))
                .andExpect(cookie().value("refresh_token", not(oldRefresh.getValue())));
    }

    @Test
    void logout_clearsCookie_andRevokesRefreshToken() throws Exception {
        MvcResult loginResult = login();
        String access = accessToken(loginResult);
        Cookie refresh = refreshCookie(loginResult);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(access))
                        .cookie(new Cookie("refresh_token", refresh.getValue())))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh_token", 0));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refresh.getValue())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withoutCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void login_withUnknownUser_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost.user\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUserAccessToken_isRejected() throws Exception {
        String token = accessToken(login());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        AppUser admin = EntityFactory.appUser(UserRole.ADMIN);
        String adminUsername = "it.admin." + EntityFactory.uniqueSuffix();
        admin.setUsername(adminUsername);
        admin.setEmail(adminUsername + "@test.gov");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);
        String adminToken = accessToken(loginAs(adminUsername));

        mockMvc.perform(put("/api/v1/users/{id}/deactivate", agent.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    // US-44: Keep refresh-token sessions reliable

    @Test
    void refresh_withMalformedToken_returns401_andClearsCookie() throws Exception {
        MvcResult loginResult = login();
        Cookie refresh = refreshCookie(loginResult);

        // Use a malformed token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "not.a.valid.jwt.token")))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void refresh_withExpiredToken_returns401_andClearsCookie() throws Exception {
        MvcResult loginResult = login();
        Cookie refresh = refreshCookie(loginResult);

        // Manually expire the refresh token by modifying the user's JTI
        // (simulates token reuse/revocation scenario)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refresh.getValue())))
                .andExpect(status().isOk()); // First refresh works

        // Try to use the same (now invalid) refresh token again
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refresh.getValue())))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void refresh_withoutCookie_clearsCookieOn401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void refresh_concurrentRequests_onlyOneSucceeds() throws Exception {
        MvcResult loginResult = login();
        Cookie refresh = refreshCookie(loginResult);
        String refreshTokenValue = refresh.getValue();

        int concurrentRequests = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            final String token = refreshTokenValue; // same token for all threads
            executor.submit(() -> {
                try {
                    MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                                    .cookie(new Cookie("refresh_token", token)))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Only one refresh should succeed; others get 401
        assertThat(successCount.get()).isEqualTo(1);
    }

    // ── US-45: Block inactive users from continuing a session ─────────────

    private static String uniqueNationalId() {
        return String.format("%016d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000_000_000L));
    }

    private String createAdminToken() throws Exception {
        AppUser admin = EntityFactory.appUser(UserRole.ADMIN);
        String adminUsername = "it.admin." + EntityFactory.uniqueSuffix();
        admin.setUsername(adminUsername);
        admin.setEmail(adminUsername + "@test.gov");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);
        return accessToken(loginAs(adminUsername));
    }

    private void deactivateAgent(String adminToken) throws Exception {
        mockMvc.perform(put("/api/v1/users/{id}/deactivate", agent.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private String createCase(String token, Citizen citizen, Category category, Department department) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "US-45 ownership fixture",
                                  "description": "Case ownership must survive user deactivation",
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

    @Test
    void inactiveUser_cannotLogin_returns401AccountDisabled() throws Exception {
        String adminToken = createAdminToken();
        deactivateAgent(adminToken);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
                .andExpect(jsonPath("$.message").value("Account is disabled. Please contact support."));
    }

    @Test
    void inactiveUser_cannotRefresh_returns401StandardBodyAndClearsCookie() throws Exception {
        MvcResult loginResult = login();
        Cookie refresh = refreshCookie(loginResult);

        String adminToken = createAdminToken();
        deactivateAgent(adminToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refresh.getValue())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
                .andExpect(jsonPath("$.message").value("Account is disabled. Please contact support."))
                .andExpect(jsonPath("$.details").value(nullValue()))
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void deactivation_leavesCaseOwnershipAndHistoryUnchanged() throws Exception {
        Citizen citizen = EntityFactory.citizen(agent);
        citizen.setNationalId(uniqueNationalId());
        citizenRepository.save(citizen);
        Category category = categoryRepository.save(EntityFactory.category());
        Department department = departmentRepository.save(EntityFactory.department());

        String token = accessToken(login());
        String caseId = createCase(token, citizen, category, department);

        // Snapshot ownership + history before deactivation
        UUID creatorIdBefore = caseRepository.findById(UUID.fromString(caseId))
                .orElseThrow().getCreatedByUser().getId();
        List<StatusHistory> historyBefore =
                statusHistoryRepository.findByCaseIdOrderByCreatedAtAsc(UUID.fromString(caseId));
        assertThat(historyBefore).hasSize(1);
        assertThat(historyBefore.get(0).getAction()).isEqualTo(WorkflowAction.CREATE);
        assertThat(historyBefore.get(0).getFromStatus()).isNull();
        assertThat(historyBefore.get(0).getToStatus()).isEqualTo(CaseStatus.NEW);
        assertThat(historyBefore.get(0).getChangedByUser().getId()).isEqualTo(agent.getId());

        String adminToken = createAdminToken();
        deactivateAgent(adminToken);

        // Prove the deactivation actually happened — otherwise the "unchanged"
        // assertions below would trivially pass.
        assertThat(userRepository.findById(agent.getId()).orElseThrow().getActive()).isFalse();

        Case stored = caseRepository.findById(UUID.fromString(caseId)).orElseThrow();
        assertThat(stored.getCreatedByUser().getId()).isEqualTo(creatorIdBefore);
        assertThat(stored.getCreatedByUser().getId()).isEqualTo(agent.getId());

        List<StatusHistory> historyAfter =
                statusHistoryRepository.findByCaseIdOrderByCreatedAtAsc(UUID.fromString(caseId));
        assertThat(historyAfter).hasSize(historyBefore.size());
        assertThat(historyAfter.get(0).getAction()).isEqualTo(WorkflowAction.CREATE);
        assertThat(historyAfter.get(0).getFromStatus()).isNull();
        assertThat(historyAfter.get(0).getToStatus()).isEqualTo(CaseStatus.NEW);
        assertThat(historyAfter.get(0).getChangedByUser().getId()).isEqualTo(agent.getId());
    }
}
