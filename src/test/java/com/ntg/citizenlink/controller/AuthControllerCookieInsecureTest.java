package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.config.TestSecurityConfig;
import com.ntg.citizenlink.dto.EncryptedAuthResponse;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.security.JwtBlocklist;
import com.ntg.citizenlink.security.config.JwtProperties;
import com.ntg.citizenlink.service.interfaces.AuthService;
import com.ntg.citizenlink.service.interfaces.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the refresh-cookie Secure flag respects an operator's explicit
 * JWT_REFRESH_COOKIE_SECURE=false override: dropped on plaintext requests, but
 * still applied when the request to the server itself is secure (the isSecure()
 * safety net keeps the cookie Secure on direct-TLS deployments).
 */
@WebMvcTest(AuthController.class)
@Import({TestSecurityConfig.class, AuthControllerCookieInsecureTest.TestBeans.class})
class AuthControllerCookieInsecureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlocklist jwtBlocklist;

    @TestConfiguration
    static class TestBeans {

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "test-secret-0123456789abcdef0123456789abcdef",
                    900_000L,
                    604_800_000L,
                    false);
        }
    }

    private EncryptedAuthResponse authResponse() {
        return new EncryptedAuthResponse(
                "access-token", "refresh-token-xyz", "enc-id", "agent01", "Agent One",
                "agent@example.gov", UserRole.AGENT);
    }

    @Test
    void login_onPlainRequest_doesNotMarkRefreshCookieSecure_whenDisabled() throws Exception {
        when(authService.login(any())).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent01\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        not(containsString("Secure"))));
    }

    @Test
    void login_onTlsRequest_stillMarksRefreshCookieSecure_whenDisabled() throws Exception {
        when(authService.login(any())).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent01\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("Secure")));
    }
}