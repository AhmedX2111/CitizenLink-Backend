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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CitizenSearchIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PASSWORD = "Passw0rd!";
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        AppUser user = EntityFactory.appUser(UserRole.AGENT);
        user.setUsername("it.search." + EntityFactory.uniqueSuffix());
        user.setEmail(user.getUsername() + "@test.gov");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(user);

        MvcResult loginResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        accessToken = body.get("token").asText();
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }

    @Test
    void searchByName_partialMatch_returnsCitizen() throws Exception {
        Citizen citizen = EntityFactory.citizen(EntityFactory.appUser(UserRole.AGENT));
        citizen.setFullName("أحمد محمد");
        citizen.setFullNameNormalized("احمد محمد");
        citizen.setNationalId("1111111111111111");
        citizen.setPhone("01011111111");
        AppUser creator = EntityFactory.appUser(UserRole.AGENT);
        creator.setUsername("creator." + EntityFactory.uniqueSuffix());
        creator.setEmail(creator.getUsername() + "@test.gov");
        creator.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(creator);
        citizen.setCreatedByUser(creator);
        citizenRepository.save(citizen);

        mockMvc.perform(get("/api/v1/citizens/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("searchTerm", "احمد")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("أحمد محمد"));
    }

    @Test
    void searchByPhone_internationalFormat_normalizesAndMatches() throws Exception {
        Citizen citizen = EntityFactory.citizen(EntityFactory.appUser(UserRole.AGENT));
        citizen.setFullName(" Sara ");
        citizen.setFullNameNormalized("sara");
        citizen.setNationalId("2222222222222222");
        citizen.setPhone("01022222222");
        AppUser creator = EntityFactory.appUser(UserRole.AGENT);
        creator.setUsername("creator2." + EntityFactory.uniqueSuffix());
        creator.setEmail(creator.getUsername() + "@test.gov");
        creator.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(creator);
        citizen.setCreatedByUser(creator);
        citizenRepository.save(citizen);

        mockMvc.perform(get("/api/v1/citizens/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("searchTerm", "+201022222222")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value(" Sara "));
    }

    @Test
    void searchByNationalId_exactMatch_returnsCitizen() throws Exception {
        Citizen citizen = EntityFactory.citizen(EntityFactory.appUser(UserRole.AGENT));
        citizen.setFullName("Omar");
        citizen.setFullNameNormalized("omar");
        citizen.setNationalId("3333333333333333");
        citizen.setPhone("01033333333");
        AppUser creator = EntityFactory.appUser(UserRole.AGENT);
        creator.setUsername("creator3." + EntityFactory.uniqueSuffix());
        creator.setEmail(creator.getUsername() + "@test.gov");
        creator.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(creator);
        citizen.setCreatedByUser(creator);
        citizenRepository.save(citizen);

        mockMvc.perform(get("/api/v1/citizens/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("searchTerm", "3333333333333333")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Omar"));
    }

    @Test
    void search_emptyTerm_returnsEmptyResults() throws Exception {
        mockMvc.perform(get("/api/v1/citizens/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("searchTerm", "   ")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void search_noMatch_returnsEmptyResults() throws Exception {
        mockMvc.perform(get("/api/v1/citizens/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("searchTerm", "nonexistent")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void search_pagination_returnsPagedResponse() throws Exception {
        for (int i = 0; i < 5; i++) {
            Citizen citizen = EntityFactory.citizen(EntityFactory.appUser(UserRole.AGENT));
            citizen.setFullName("Test Citizen " + i);
            citizen.setFullNameNormalized("test citizen " + i);
            citizen.setNationalId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            citizen.setPhone(String.format("010%08d", i));
            AppUser creator = EntityFactory.appUser(UserRole.AGENT);
            creator.setUsername("pagecreator" + i + "." + EntityFactory.uniqueSuffix());
            creator.setEmail(creator.getUsername() + "@test.gov");
            creator.setPasswordHash(passwordEncoder.encode(PASSWORD));
            userRepository.save(creator);
            citizen.setCreatedByUser(creator);
            citizenRepository.save(citizen);
        }

        mockMvc.perform(get("/api/v1/citizens/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("searchTerm", "test")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void search_arabicNormalization_matchesAcrossAlefForms() throws Exception {
        Citizen citizen = EntityFactory.citizen(EntityFactory.appUser(UserRole.AGENT));
        citizen.setFullName("إبراهيم");
        citizen.setFullNameNormalized("ابراهيم");
        citizen.setNationalId("4444444444444444");
        citizen.setPhone("01044444444");
        AppUser creator = EntityFactory.appUser(UserRole.AGENT);
        creator.setUsername("creator4." + EntityFactory.uniqueSuffix());
        creator.setEmail(creator.getUsername() + "@test.gov");
        creator.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(creator);
        citizen.setCreatedByUser(creator);
        citizenRepository.save(citizen);

        mockMvc.perform(get("/api/v1/citizens/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("searchTerm", "ابراهيم")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("إبراهيم"));
    }

    @Test
    void search_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/citizens/search")
                        .param("searchTerm", "test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void search_agent_seesMaskedSensitiveFields() throws Exception {
        // Create citizen with full PII
        AppUser adminCreator = EntityFactory.appUser(UserRole.ADMIN);
        adminCreator.setUsername("admin_creator." + EntityFactory.uniqueSuffix());
        adminCreator.setEmail(adminCreator.getUsername() + "@test.gov");
        adminCreator.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(adminCreator);

        Citizen citizen = new Citizen();
        citizen.setFullName("Ahmed Mohamed");
        citizen.setFullNameNormalized("ahmed mohamed");
        citizen.setNationalId("9876543210987654");
        citizen.setPhone("01099998888");
        citizen.setEmail("ahmed@test.gov");
        citizen.setCreatedByUser(adminCreator);
        citizenRepository.save(citizen);

        // Login as AGENT and search — should see masked values
        AppUser agent = EntityFactory.appUser(UserRole.AGENT);
        agent.setUsername("agent_mask." + EntityFactory.uniqueSuffix());
        agent.setEmail(agent.getUsername() + "@test.gov");
        agent.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(agent);

        MvcResult agentLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + agent.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String agentToken = objectMapper.readTree(agentLogin.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get("/api/v1/citizens/search")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(agentToken))
                        .param("searchTerm", "ahmed")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nationalId").value("987****7654"))
                .andExpect(jsonPath("$.content[0].phone").value("010****8888"))
                .andExpect(jsonPath("$.content[0].email").value("a****@test.gov"))
                .andExpect(jsonPath("$.content[0].fullName").value("Ahmed Mohamed")); // never masked
    }

    @Test
    void search_admin_seesFullSensitiveFields() throws Exception {
        // Create citizen with full PII
        AppUser creator = EntityFactory.appUser(UserRole.ADMIN);
        creator.setUsername("admin_creator2." + EntityFactory.uniqueSuffix());
        creator.setEmail(creator.getUsername() + "@test.gov");
        creator.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(creator);

        Citizen citizen = new Citizen();
        citizen.setFullName("Sara Ahmed");
        citizen.setFullNameNormalized("sara ahmed");
        citizen.setNationalId("1112223334445555");
        citizen.setPhone("01122233344");
        citizen.setEmail("sara@test.gov");
        citizen.setCreatedByUser(creator);
        citizenRepository.save(citizen);

        // Login as ADMIN and search — should see full values
        AppUser admin = EntityFactory.appUser(UserRole.ADMIN);
        admin.setUsername("admin_full." + EntityFactory.uniqueSuffix());
        admin.setEmail(admin.getUsername() + "@test.gov");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);

        MvcResult adminLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + admin.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String adminToken = objectMapper.readTree(adminLogin.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get("/api/v1/citizens/search")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(adminToken))
                        .param("searchTerm", "sara ahmed")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nationalId").value("111****5555"))
                .andExpect(jsonPath("$.content[0].phone").value("011****3344"))
                .andExpect(jsonPath("$.content[0].email").value("s****@test.gov"));
    }

    private String bearerFor(String token) {
        return "Bearer " + token;
    }
}
