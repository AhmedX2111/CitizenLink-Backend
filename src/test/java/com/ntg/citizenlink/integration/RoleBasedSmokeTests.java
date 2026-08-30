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
import com.ntg.citizenlink.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v1.5.0 release-gate role smoke tests (gate criterion 3).
 *
 * <p>One self-contained end-to-end journey per role — AGENT, HANDLER,
 * SUPERVISOR, ADMIN — over the real HTTP surface (login, cases, workflow
 * transitions, notes, attachments, reports, user administration) and the
 * role boundaries in between (403 on endpoints the role must not reach,
 * 404 on cases the role must not see, 401 with the ACCOUNT_DISABLED
 * envelope for a deactivated account). These tests run as part of the
 * standard suite ({@code .\mvnw.cmd test}) and are the evidence for the
 * role-based smoke criterion of the v1.5.0 acceptance record
 * ({@code docs/US-43-US-45-acceptance.md}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleBasedSmokeTests {

    private static final String PASSWORD = "Passw0rd!";
    private static final byte[] PDF_BYTES = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF"
            .getBytes(StandardCharsets.UTF_8);

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppUser agent;
    private AppUser handler;
    private AppUser supervisor;
    private AppUser admin;
    private Citizen citizen;
    private Category category;
    private Department department;

    @BeforeEach
    void setUp() {
        agent = createUser(UserRole.AGENT);
        handler = createUser(UserRole.HANDLER);
        supervisor = createUser(UserRole.SUPERVISOR);
        admin = createUser(UserRole.ADMIN);
        citizen = EntityFactory.citizen(agent);
        citizen.setNationalId(uniqueNationalId());
        citizenRepository.save(citizen);
        category = categoryRepository.save(EntityFactory.category());
        department = departmentRepository.save(EntityFactory.department());
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

    private String loginExpectingDisabled(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
                .andReturn();
        return null;
    }

    private String caseBody() {
        return """
                {
                  "subject": "Role smoke case",
                  "description": "Created by role smoke test",
                  "type": "COMPLAINT",
                  "priority": "HIGH",
                  "channel": "WEB",
                  "citizenNationalId": "%s",
                  "categoryId": "%s",
                  "departmentId": "%s"
                }
                """.formatted(citizen.getNationalId(), category.getId(), department.getId());
    }

    private String createCase(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(caseBody()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void assignToHandler(String supervisorToken, String caseId) throws Exception {
        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\",\"assignedToUserId\":\"" + handler.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    private String addNote(String token, String caseId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cases/{caseId}/notes", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + body + "\",\"internal\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String uploadPdf(String token, String caseId, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, MediaType.APPLICATION_PDF_VALUE, PDF_BYTES);
        MvcResult result = mockMvc.perform(multipart("/api/v1/cases/{caseId}/attachments", caseId)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // ── US gate: AGENT role smoke journey ──────────────────────────────────

    @Test
    void agentRoleSmokeTest() throws Exception {
        String token = login(agent.getUsername());

        // Own a case end to end: create, annotate, attach.
        String caseId = createCase(token);
        String noteId = addNote(token, caseId, "Agent smoke note");
        uploadPdf(token, caseId, "smoke-report.pdf");

        mockMvc.perform(get("/api/v1/cases/{caseId}/notes", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(noteId));

        mockMvc.perform(get("/api/v1/cases/{id}", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));

        // Workflow is out of scope for an agent: transitions are forbidden.
        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\",\"assignedToUserId\":\"" + handler.getId() + "\"}"))
                .andExpect(status().isForbidden());

        // Another agent's case must be invisible (404, not 403).
        AppUser otherAgent = createUser(UserRole.AGENT);
        String otherCaseId = createCase(login(otherAgent.getUsername()));
        mockMvc.perform(get("/api/v1/cases/{id}", otherCaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        // User administration is ADMIN-only.
        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── US gate: HANDLER role smoke journey ────────────────────────────────

    @Test
    void handlerRoleSmokeTest() throws Exception {
        // Fixture: the agent creates two cases — one later assigned to the
        // handler, one left unassigned — and authors a note on the assigned one.
        String agentToken = login(agent.getUsername());
        String assignedCaseId = createCase(agentToken);
        String agentNoteId = addNote(agentToken, assignedCaseId, "Agent-authored note");
        String unassignedCaseId = createCase(agentToken);
        assignToHandler(login(supervisor.getUsername()), assignedCaseId);

        String handlerToken = login(handler.getUsername());

        // The handler sees only the case assigned to them.
        mockMvc.perform(get("/api/v1/cases/{id}", assignedCaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        // START is a handler action.
        mockMvc.perform(post("/api/v1/cases/{id}/transition", assignedCaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // A handler can annotate the assigned case...
        addNote(handlerToken, assignedCaseId, "Handler smoke note");

        // ...but must not edit another author's note on it.
        mockMvc.perform(put("/api/v1/cases/{caseId}/notes/{noteId}", assignedCaseId, agentNoteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"handler tampered\"}"))
                .andExpect(status().isForbidden());

        // An unassigned case stays invisible to the handler.
        mockMvc.perform(get("/api/v1/cases/{id}", unassignedCaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken))
                .andExpect(status().isNotFound());

        // User administration is ADMIN-only.
        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken))
                .andExpect(status().isForbidden());
    }

    // ── US gate: SUPERVISOR role smoke journey ─────────────────────────────

    @Test
    void supervisorRoleSmokeTest() throws Exception {
        String token = login(supervisor.getUsername());

        // A supervisor can create cases and drive the workflow.
        String caseId = createCase(token);
        assignToHandler(token, caseId);

        // Cross-visibility: a case created by an agent is fully visible.
        String agentCaseId = createCase(login(agent.getUsername()));
        mockMvc.perform(get("/api/v1/cases/{id}", agentCaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));

        // Handlers directory and the read-only volume report are in scope.
        mockMvc.perform(get("/api/v1/users/handlers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(handler.getId().toString())));

        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/v1/reports/volume")
                        .param("from", today)
                        .param("to", today)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyVolume").exists());

        // Full user administration is ADMIN-only.
        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── US gate: ADMIN role smoke journey ──────────────────────────────────

    @Test
    void adminRoleSmokeTest() throws Exception {
        // Fixture: the agent owns a case with an authored note.
        String agentToken = login(agent.getUsername());
        String caseId = createCase(agentToken);
        String noteId = addNote(agentToken, caseId, "Agent smoke note");

        String adminToken = login(admin.getUsername());

        // Full administration surface: users list, handlers directory, any case.
        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());

        mockMvc.perform(get("/api/v1/users/handlers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/cases/{id}", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));

        // Deactivate the agent: their next login must be refused with the
        // standard ACCOUNT_DISABLED envelope (US-45).
        mockMvc.perform(put("/api/v1/users/{id}/deactivate", agent.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        loginExpectingDisabled(agent.getUsername());

        // Reactivation restores login (US-45).
        mockMvc.perform(put("/api/v1/users/{id}/activate", agent.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        login(agent.getUsername());

        // Admin overrides the author-only note rule: deleting the agent's
        // note on the agent's case succeeds (204 NO CONTENT).
        mockMvc.perform(delete("/api/v1/cases/{caseId}/notes/{noteId}", caseId, noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}
