package com.ntg.CitizenLink.integration;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseFlowIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppUser supervisor;
    private AppUser handler;
    private Citizen citizen;
    private Category category;
    private Department department;
    private String supervisorToken;

    @BeforeEach
    void setUp() throws Exception {
        supervisor = createUser(UserRole.SUPERVISOR);
        handler = createUser(UserRole.HANDLER);
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

    private String createBody() {
        return """
                {
                  "subject": "Integration water leak",
                  "description": "Created by integration test",
                  "type": "COMPLAINT",
                  "priority": "HIGH",
                  "channel": "WEB",
                  "citizenNationalId": "%s",
                  "categoryId": "%s",
                  "departmentId": "%s"
                }
                """.formatted(citizen.getNationalId(), category.getId(), department.getId());
    }

    private String createCase() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void create_returns201_withGeneratedCaseNumber() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseNumber").isNotEmpty())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("caseNumber").asText()).matches("CASE-\\d{4}-\\d{5}");
    }

    @Test
    void fullLifecycle_createAssignStartResolve() throws Exception {
        String caseId = createCase();

        mockMvc.perform(get("/api/v1/cases/{id}/actions", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItems("ASSIGN", "CANCEL")));

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\",\"assignedToUserId\":\"" + handler.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        mockMvc.perform(get("/api/v1/cases/{id}/timeline", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        String handlerToken = login(handler.getUsername());

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"RESOLVE\",\"resolutionSummary\":\"Leak fixed on site\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionSummary").value("Leak fixed on site"));
    }

    @Test
    void getById_returns404_whenNotVisibleToAgent() throws Exception {
        String caseId = createCase();
        AppUser agent = createUser(UserRole.AGENT);
        String agentToken = login(agent.getUsername());

        mockMvc.perform(get("/api/v1/cases/{id}", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentCannotTransition() throws Exception {
        String caseId = createCase();
        AppUser agent = createUser(UserRole.AGENT);
        String agentToken = login(agent.getUsername());

        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\",\"assignedToUserId\":\"" + handler.getId() + "\"}"))
                .andExpect(status().isForbidden());
    }

    private String createBodyWithAssignment(UUID assignedToUserId) {
        return """
                {
                  "subject": "Integration pre-assigned case",
                  "description": "Created by integration test",
                  "type": "COMPLAINT",
                  "priority": "HIGH",
                  "channel": "WEB",
                  "citizenNationalId": "%s",
                  "categoryId": "%s",
                  "departmentId": "%s",
                  "assignedToUserId": "%s"
                }
                """.formatted(citizen.getNationalId(), category.getId(), department.getId(), assignedToUserId);
    }

    @Test
    void agentCreate_withAssignedToUser_isIgnoredAndCaseStaysNew() throws Exception {
        AppUser agent = createUser(UserRole.AGENT);
        String agentToken = login(agent.getUsername());
        String handlerToken = login(handler.getUsername());

        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBodyWithAssignment(handler.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn();
        String caseId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // The target handler must NOT gain visibility of the case.
        mockMvc.perform(get("/api/v1/cases/{id}", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken))
                .andExpect(status().isNotFound());

        // No ASSIGN timeline entry — only CREATE.
        mockMvc.perform(get("/api/v1/cases/{id}/timeline", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action").value("CREATE"));
    }

    @Test
    void supervisorCreate_withAssignedToHandler_setsAssignedStatus() throws Exception {
        String handlerToken = login(handler.getUsername());

        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBodyWithAssignment(handler.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andReturn();
        String caseId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // The handler gains visibility immediately.
        mockMvc.perform(get("/api/v1/cases/{id}", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken))
                .andExpect(status().isOk());

        // CREATE + ASSIGN timeline entries.
        mockMvc.perform(get("/api/v1/cases/{id}/timeline", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].action", hasItems("CREATE", "ASSIGN")));
    }

    @Test
    void supervisorCreate_withAssignedToNonHandler_isRejected() throws Exception {
        AppUser targetAgent = createUser(UserRole.AGENT);

        mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBodyWithAssignment(targetAgent.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedCreate_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isForbidden());
    }
}
