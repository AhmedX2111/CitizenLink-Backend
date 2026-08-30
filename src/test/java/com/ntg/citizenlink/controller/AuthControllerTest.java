package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.config.TestSecurityConfig;
import com.ntg.citizenlink.dto.EncryptedAuthResponse;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.security.JwtBlocklist;
import com.ntg.citizenlink.security.config.JwtProperties;
import com.ntg.citizenlink.service.interfaces.AuthService;
import com.ntg.citizenlink.service.interfaces.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({TestSecurityConfig.class, AuthControllerTest.TestBeans.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlocklist jwtBlocklist;

    private static final String ACCESS = "access-token";
    private static final String REFRESH = "refresh-token-xyz";

    private EncryptedAuthResponse authResponse(String access, String refresh) {
        return new EncryptedAuthResponse(
                access, refresh, "enc-id", "agent01", "Agent One",
                "agent@example.gov", UserRole.AGENT);
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "test-secret-0123456789abcdef0123456789abcdef",
                    900_000L,
                    604_800_000L,
                    true);
        }
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/auth/login
    // ---------------------------------------------------------------------

    @Test
    void login_returns200_withAccessTokenInBodyAndRefreshCookie() throws Exception {
        when(authService.login(any())).thenReturn(authResponse(ACCESS, REFRESH));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent01\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(ACCESS))
                .andExpect(jsonPath("$.refreshToken").value(nullValue()))
                .andExpect(jsonPath("$.username").value("agent01"))
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("refresh_token=" + REFRESH)))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("Path=/api/v1/auth")));
    }

    @Test
    void login_marksRefreshCookieSecureByDefault() throws Exception {
        when(authService.login(any())).thenReturn(authResponse(ACCESS, REFRESH));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent01\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")));
    }

    @Test
    void login_returns400_whenValidationFails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(authService, never()).login(any());
    }

    @Test
    void login_returns400_whenCredentialsExceedMaxLength() throws Exception {
        String longUsername = "u".repeat(101);
        String longPassword = "p".repeat(129);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + longUsername
                                + "\",\"password\":\"" + longPassword + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(authService, never()).login(any());
    }

    @Test
    void login_returns401_whenBadCredentials() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent01\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/auth/refresh
    // ---------------------------------------------------------------------

    @Test
    void refresh_returns200_withNewAccessTokenAndRotatedCookie() throws Exception {
        when(authService.refreshToken(REFRESH))
                .thenReturn(authResponse("new-access", "new-refresh"));
        when(jwtService.extractUsername(REFRESH)).thenReturn("agent01");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", REFRESH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value(nullValue()))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("refresh_token=new-refresh")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("Secure")));
    }

    @Test
    void refresh_returns401_whenCookieMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).refreshToken(any());
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/auth/logout
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser
    void logout_returns200_andClearsRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh_token", 0));

        verify(authService).logout(any());
    }

    @Test
    @WithMockUser
    void logout_withoutBearerToken_doesNotTouchBlocklist() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk());

        verify(jwtBlocklist, never()).block(any(), any());
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/auth/me
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(username = "agent01")
    void me_returns200_withCurrentUserProfile() throws Exception {
        when(authService.getCurrentUser("agent01"))
                .thenReturn(authResponse(null, null));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("agent01"))
                .andExpect(jsonPath("$.displayName").value("Agent One"))
                .andExpect(jsonPath("$.role").value("AGENT"));
    }

    @Test
    void me_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
