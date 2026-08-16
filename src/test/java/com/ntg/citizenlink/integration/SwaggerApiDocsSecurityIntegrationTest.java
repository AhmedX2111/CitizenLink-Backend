package com.ntg.citizenlink.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerApiDocsSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiEndpoints_arePublicAndServedOutsideProd() throws Exception {
        // M-08: outside prod the dedicated @Profile("!prod") filter chain keeps the
        // OpenAPI/Swagger matchers permitAll, so an anonymous request reaches the
        // springdoc controllers — the schema is generated and the UI is served.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        // /swagger-ui.html is springdoc's redirect entry page; the security
        // property is that it is NOT rejected with 401/403.
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void protectedApiEndpoints_stillRequireAuthOutsideProd() throws Exception {
        // Control: the real API surface stays authenticated even outside prod.
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}