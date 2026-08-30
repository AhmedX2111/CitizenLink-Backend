package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.config.TestSecurityConfig;
import com.ntg.citizenlink.exception.BusinessRuleException;
import com.ntg.citizenlink.exception.InvalidEncryptedIdException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
    void maxUploadSizeExceeded_returns413_withStandardEnvelope() throws Exception {
        // US-46 AC6: the container-level upload limit (MaxUploadSizeExceededException)
        // must map to 413 with the standard error envelope, not a 500.
        when(caseNoteService.getNotesByCaseId(any(), any()))
                .thenThrow(new MaxUploadSizeExceededException(5L * 1024 * 1024));
        mockMvc.perform(get("/api/v1/cases/{caseId}/notes", CASE_ID))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Upload exceeds the maximum allowed size"));
    }

    @Test
    void genuineServerError_stillReturns500() throws Exception {
        when(caseNoteService.getNotesByCaseId(any(), any())).thenThrow(new RuntimeException("boom"));
        mockMvc.perform(get("/api/v1/cases/{caseId}/notes", CASE_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void invalidEncryptedId_returns400_withEnvelope() throws Exception {
        when(caseNoteService.getNotesByCaseId(any(), any()))
                .thenThrow(new InvalidEncryptedIdException("Invalid or corrupted encrypted ID."));
        mockMvc.perform(get("/api/v1/cases/{caseId}/notes", CASE_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ENCRYPTED_ID"))
                .andExpect(jsonPath("$.message").value("Invalid or corrupted encrypted ID."));
    }

    @Test
    void dataIntegrityViolation_returns409_withGenericEnvelope() throws Exception {
        // M-15: a DB constraint failure (message contains the conflicting
        // national ID + schema) must map to a generic 409 — the identifier is
        // never echoed to the client.
        when(caseNoteService.getNotesByCaseId(any(), any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement; constraint [citizen_unique_national_id]; "
                + "nested exception is org.postgresql.util.PSQLException: "
                + "ERROR: duplicate key value violates unique constraint \"citizen_unique_national_id\" "
                + "Detail: Key (national_id)=(4123456789012) already exists."));
        mockMvc.perform(get("/api/v1/cases/{caseId}/notes", CASE_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.message").value("A record with this value already exists."))
                .andExpect(jsonPath("$.message").value(not(containsString("4123456789012"))));
    }

    @Test
    void volumeReport_withExcessiveRange_returns400() throws Exception {
        // M-16/L-11: an unbounded range is rejected with 400 BAD_REQUEST. The
        // date-range validator raises a BusinessRuleException whose message is
        // intentional and client-facing, so the handler surfaces it verbatim.
        when(reportService.getVolumeReport(any(), any()))
                .thenThrow(new BusinessRuleException(
                        "Requested date range exceeds the maximum allowed span of 366 days"));
        mockMvc.perform(get("/api/v1/reports/volume")
                        .param("from", "0001-01-01")
                        .param("to", "9999-12-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("Requested date range exceeds the maximum allowed span of 366 days"));
    }

    @Test
    void illegalArgumentException_returns400_withOpaqueMessage() throws Exception {
        // L-11: a raw IllegalArgumentException's message is NOT written for the
        // client (it may embed internal values such as stored file names or
        // detected MIME types). The handler must surface a fixed opaque message
        // and never echo the exception text.
        when(reportService.getVolumeReport(any(), any()))
                .thenThrow(new IllegalArgumentException("Sensitive internal detail"));
        mockMvc.perform(get("/api/v1/reports/volume")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.message").value(not(containsString("Sensitive"))));
    }
}