package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.config.TestSecurityConfig;
import com.ntg.citizenlink.dto.agent.response.CaseActionResponse;
import com.ntg.citizenlink.dto.agent.response.CaseResponse;
import com.ntg.citizenlink.dto.agent.response.PagedResponse;
import com.ntg.citizenlink.dto.agent.response.StatusHistoryResponse;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.CaseType;
import com.ntg.citizenlink.enums.Channel;
import com.ntg.citizenlink.enums.Priority;
import com.ntg.citizenlink.enums.WorkflowAction;
import com.ntg.citizenlink.exception.ResourceNotFoundException;
import com.ntg.citizenlink.security.JwtBlocklist;
import com.ntg.citizenlink.security.config.SecurityContextHelper;
import com.ntg.citizenlink.service.interfaces.CaseService;
import com.ntg.citizenlink.service.interfaces.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseController.class)
@Import(TestSecurityConfig.class)
class CaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CaseService caseService;

    @MockitoBean
    private SecurityContextHelper securityContextHelper;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlocklist jwtBlocklist;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CASE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String VALID_CREATE_BODY = """
            {
              "subject": "Water leak",
              "description": "Leak on the main road",
              "type": "COMPLAINT",
              "priority": "HIGH",
              "channel": "PHONE",
              "citizenNationalId": "1234567890123456",
              "categoryId": "33333333-3333-3333-3333-333333333333",
              "departmentId": "44444444-4444-4444-4444-444444444444"
            }
            """;

    private CaseResponse caseResponse() {
        CaseResponse r = new CaseResponse();
        r.setId(CASE_ID);
        r.setCaseNumber("CASE-2026-00001");
        r.setSubject("Water leak");
        r.setDescription("Leak on the main road");
        r.setType(CaseType.COMPLAINT);
        r.setPriority(Priority.HIGH);
        r.setStatus(CaseStatus.NEW);
        r.setChannel(Channel.PHONE);
        return r;
    }

    private StatusHistoryResponse statusHistory() {
        return StatusHistoryResponse.builder()
                .id(UUID.randomUUID())
                .fromStatus(null)
                .toStatus(CaseStatus.NEW)
                .action(WorkflowAction.CREATE)
                .createdAt(OffsetDateTime.now())
                .changedByDisplayName("Agent One")
                .build();
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/cases
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "AGENT")
    void create_returns201_asAgent() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(caseService.createCase(any(), eq(USER_ID))).thenReturn(caseResponse());

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CASE_ID.toString()))
                .andExpect(jsonPath("$.caseNumber").value("CASE-2026-00001"))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns201_asAdmin() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(caseService.createCase(any(), eq(USER_ID))).thenReturn(caseResponse());

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void create_returns400_whenValidationFails() throws Exception {
        String body = """
                {
                  "subject": "",
                  "description": "Leak on the main road",
                  "type": "COMPLAINT",
                  "priority": "HIGH",
                  "channel": "PHONE",
                  "citizenNationalId": "1234567890123456",
                  "categoryId": "33333333-3333-3333-3333-333333333333",
                  "departmentId": "44444444-4444-4444-4444-444444444444"
                }
                """;

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(caseService, never()).createCase(any(), any());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void create_returns400_whenDescriptionTooLong() throws Exception {
        String tooLong = "d".repeat(5001);
        String body = """
                {
                  "subject": "Water leak",
                  "description": "%s",
                  "type": "COMPLAINT",
                  "priority": "HIGH",
                  "channel": "PHONE",
                  "citizenNationalId": "1234567890123456",
                  "categoryId": "33333333-3333-3333-3333-333333333333",
                  "departmentId": "44444444-4444-4444-4444-444444444444"
                }
                """.formatted(tooLong);

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(caseService, never()).createCase(any(), any());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void create_returns201_whenDescriptionAtMaxLength() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(caseService.createCase(any(), eq(USER_ID))).thenReturn(caseResponse());

        String atLimit = "d".repeat(5000);
        String body = """
                {
                  "subject": "Water leak",
                  "description": "%s",
                  "type": "COMPLAINT",
                  "priority": "HIGH",
                  "channel": "PHONE",
                  "citizenNationalId": "1234567890123456",
                  "categoryId": "33333333-3333-3333-3333-333333333333",
                  "departmentId": "44444444-4444-4444-4444-444444444444"
                }
                """.formatted(atLimit);

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void create_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void create_returns403_whenRoleInsufficient() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/cases (search)
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "HANDLER")
    void search_returns200_asHandler() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(caseService.searchCases(any(), eq(USER_ID)))
                .thenReturn(new PagedResponse<>(List.of(caseResponse()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/cases")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].caseNumber").value("CASE-2026-00001"));
    }

    @Test
    @WithMockUser
    void search_returns403_whenRoleInsufficient() throws Exception {
        mockMvc.perform(get("/api/v1/cases"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/cases/{id}
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "AGENT")
    void getById_returns200_asAgent() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(caseService.getCaseById(CASE_ID, USER_ID)).thenReturn(caseResponse());

        mockMvc.perform(get("/api/v1/cases/" + CASE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CASE_ID.toString()));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void getById_returns404_whenCaseNotFound() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(caseService.getCaseById(CASE_ID, USER_ID))
                .thenThrow(new ResourceNotFoundException("Case not found"));

        mockMvc.perform(get("/api/v1/cases/" + CASE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getById_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/cases/" + CASE_ID))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/cases/{id}/timeline
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "AGENT")
    void timeline_returns200_asAgent() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(caseService.getCaseTimeline(CASE_ID, USER_ID))
                .thenReturn(List.of(statusHistory()));

        mockMvc.perform(get("/api/v1/cases/" + CASE_ID + "/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].toStatus").value("NEW"))
                .andExpect(jsonPath("$[0].action").value("CREATE"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void timeline_returns200_asSupervisor() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(caseService.getCaseTimeline(CASE_ID, USER_ID))
                .thenReturn(List.of(statusHistory()));

        mockMvc.perform(get("/api/v1/cases/" + CASE_ID + "/timeline"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void timeline_returns403_whenRoleInsufficient() throws Exception {
        mockMvc.perform(get("/api/v1/cases/" + CASE_ID + "/timeline"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/cases/{id}/actions
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "HANDLER")
    void actions_returns200_asHandler() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(caseService.getCaseActions(CASE_ID, USER_ID))
                .thenReturn(List.of(new CaseActionResponse(
                        WorkflowAction.START, "cases.actions.start",
                        CaseStatus.IN_PROGRESS, false, false)));

        mockMvc.perform(get("/api/v1/cases/" + CASE_ID + "/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action").value("START"))
                .andExpect(jsonPath("$[0].resultingStatus").value("IN_PROGRESS"));
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/cases/{id}/transition
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "HANDLER")
    void transition_returns200_asHandler() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(caseService.transitionCase(eq(CASE_ID), eq(USER_ID), any()))
                .thenReturn(caseResponse());

        mockMvc.perform(post("/api/v1/cases/" + CASE_ID + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void transition_returns403_asAgent() throws Exception {
        mockMvc.perform(post("/api/v1/cases/" + CASE_ID + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START\"}"))
                .andExpect(status().isForbidden());

        verify(caseService, never()).transitionCase(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void transition_returns400_whenActionMissing() throws Exception {
        mockMvc.perform(post("/api/v1/cases/" + CASE_ID + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(roles = "HANDLER")
    void transition_returns400_whenCommentTooLong() throws Exception {
        String tooLong = "c".repeat(5001);
        mockMvc.perform(post("/api/v1/cases/" + CASE_ID + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SUSPEND\",\"comment\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(caseService, never()).transitionCase(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "HANDLER")
    void transition_returns400_whenResolutionSummaryTooLong() throws Exception {
        String tooLong = "r".repeat(5001);
        mockMvc.perform(post("/api/v1/cases/" + CASE_ID + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"RESOLVE\",\"resolutionSummary\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(caseService, never()).transitionCase(any(), any(), any());
    }
}
