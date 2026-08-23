package com.ntg.citizenlink.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.AppUserRepository;
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
}
