package com.ntg.citizenlink.integration;

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
import com.ntg.citizenlink.security.config.JwtProperties;
import com.ntg.citizenlink.support.EntityFactory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-47: security-chain failures must return the standard
 * {@code {code, message, details}} envelope (matching the MVC-layer schema)
 * instead of the previous empty-body 401/403 responses.
 *
 * Covers:
 *   - missing / malformed / expired credentials → 401 envelope
 *   - URL-level role denial → 403 envelope (new AccessDeniedHandler)
 *   - method-security denial → 403 envelope (existing @ControllerAdvice)
 *   - hidden-resource 404 policy unchanged → 404 envelope
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityErrorEnvelopeIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProperties jwtProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    // ── Helpers (mirror AuthFlowIntegrationTest) ──────────────────────────

    private AppUser createUser(UserRole role) {
        String user = "it." + EntityFactory.uniqueSuffix();
        AppUser entity = EntityFactory.appUser(role);
        entity.setUsername(user);
        entity.setEmail(user + "@test.gov");
        entity.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userRepository.save(entity);
    }

    private MvcResult login(String user) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String accessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueNationalId() {
        return String.format("%016d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000_000_000L));
    }

    private String createCase(String token) throws Exception {
        Citizen citizen = EntityFactory.citizen(agent);
        citizen.setNationalId(uniqueNationalId());
        citizenRepository.save(citizen);
        Category category = categoryRepository.save(EntityFactory.category());
        Department department = departmentRepository.save(EntityFactory.department());

        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "US-47 security error envelope fixture",
                                  "description": "Created to exercise 403/404 envelopes",
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

    /**
     * Deterministically mints an already-expired, correctly HS-signed access
     * token for the given subject using the same secret as the running app.
     */
    private String expiredToken(String subject) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date(now.getTime() - 2 * 60 * 60 * 1000))
                .expiration(new Date(now.getTime() - 60 * 60 * 1000))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    // ── 401: missing / malformed / expired credentials ────────────────────

    @Test
    void unauthenticatedRequest_toProtectedEndpoint_returns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void malformedBearerToken_returns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void expiredBearerToken_returns401Envelope() throws Exception {
        String token = expiredToken(username);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
    }

    // ── 403: URL-level and method-security role denial ────────────────────

    @Test
    void authenticatedAgent_callingAdminOnlyUrl_returns403Envelope() throws Exception {
        String token = accessToken(login(username));

        // /api/v1/users/** is hasRole("ADMIN") in SecurityConfig — an AGENT hits
        // the new AccessDeniedHandler (filter chain), not the MVC handler.
        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void agentCallingTransitionEndpoint_returns403Envelope() throws Exception {
        String token = accessToken(login(username));
        String caseId = createCase(token);

        // POST /api/v1/cases/{id}/transition is @PreAuthorize("HANDLER,SUPERVISOR,ADMIN").
        // An AGENT is denied at the method-security layer (MVC handler), which must
        // also emit the FORBIDDEN envelope.
        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    // ── 404: hidden-resource policy unchanged ─────────────────────────────

    @Test
    void anotherAgentsCase_returns404Envelope() throws Exception {
        String ownerToken = accessToken(login(username));
        String caseId = createCase(ownerToken);

        AppUser other = createUser(UserRole.AGENT);
        String otherToken = accessToken(login(other.getUsername()));

        mockMvc.perform(get("/api/v1/cases/{id}", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
