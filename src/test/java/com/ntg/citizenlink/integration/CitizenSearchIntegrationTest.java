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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
