package com.ntg.citizenlink.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
class SwaggerApiDocsSecurityProdIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiEndpoints_requireAuthInProd() throws Exception {
        // M-08: under prod the @Profile("!prod") swagger chain is not created, so the
        // OpenAPI/Swagger paths fall through to the main chain's
        // .anyRequest().authenticated() and an anonymous reader gets 401 instead of
        // the public API surface.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isUnauthorized());
    }
}