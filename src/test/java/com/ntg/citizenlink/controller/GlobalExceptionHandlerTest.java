package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.config.TestSecurityConfig;
import com.ntg.citizenlink.security.JwtBlocklist;
import com.ntg.citizenlink.security.config.SecurityContextHelper;
import com.ntg.citizenlink.service.ReportService;
import com.ntg.citizenlink.service.interfaces.CaseNoteService;
import com.ntg.citizenlink.service.interfaces.CsvExportService;
import com.ntg.citizenlink.service.interfaces.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M-03 regression tests: the catch-all exception handler must not downgrade
 * Spring MVC client errors (4xx) to 500.
 */
@WebMvcTest({ReportController.class, CaseNoteController.class})
@Import(TestSecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class GlobalExceptionHandlerTest {

    private static final String CASE_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private CsvExportService csvExportService;

    @MockitoBean
    private CaseNoteService caseNoteService;

    @MockitoBean
    private SecurityContextHelper securityContextHelper;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlocklist jwtBlocklist;

    @Test
    void malformedJsonBody_returns400_not500() throws Exception {
        mockMvc.perform(post("/api/v1/cases/{caseId}/notes", CASE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void missingRequiredParameter_returns400_not500() throws Exception {
        mockMvc.perform(get("/api/v1/reports/volume"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("Required request parameter 'from' is not present"));
    }

    @Test
    void pageSizeOverMax_returns400_not500() throws Exception {
        mockMvc.perform(get("/api/v1/cases/{caseId}/notes/paginated", CASE_ID)
                        .param("page", "0")
                        .param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("size"));
    }

    @Test
    void pageSizeBelowMin_returns400_not500() throws Exception {
        mockMvc.perform(get("/api/v1/cases/{caseId}/notes/paginated", CASE_ID)
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void methodNotAllowed_returns405_withEnvelope() throws Exception {
        mockMvc.perform(delete("/api/v1/reports/volume"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedMediaType_returns415_withEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/cases/{caseId}/notes", CASE_ID)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void genuineServerError_stillReturns500() throws Exception {
        when(caseNoteService.getNotesByCaseId(any(), any())).thenThrow(new RuntimeException("boom"));
        mockMvc.perform(get("/api/v1/cases/{caseId}/notes", CASE_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}