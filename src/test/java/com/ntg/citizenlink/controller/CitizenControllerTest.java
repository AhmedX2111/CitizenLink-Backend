package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.config.TestSecurityConfig;
import com.ntg.citizenlink.dto.agent.request.CreateCitizenCaseRequest;
import com.ntg.citizenlink.dto.agent.response.CaseResponse;
import com.ntg.citizenlink.dto.agent.response.CaseSummaryResponse;
import com.ntg.citizenlink.dto.agent.response.DuplicateCaseCandidateResponse;
import com.ntg.citizenlink.dto.agent.response.PagedResponse;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.CaseType;
import com.ntg.citizenlink.enums.Channel;
import com.ntg.citizenlink.enums.Priority;
import com.ntg.citizenlink.exception.ResourceNotFoundException;
import com.ntg.citizenlink.security.JwtBlocklist;
import com.ntg.citizenlink.security.config.SecurityContextHelper;
import com.ntg.citizenlink.service.interfaces.CaseService;
import com.ntg.citizenlink.service.interfaces.CitizenService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-57: POST /api/v1/citizens/{id}/cases — creates a case directly from
 * the Citizen 360 screen with the citizen locked by the URL path.
 */
@WebMvcTest(CitizenController.class)
@Import(TestSecurityConfig.class)
class CitizenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CitizenService citizenService;

    @MockitoBean
    private CaseService caseService;

    @MockitoBean
    private SecurityContextHelper securityContextHelper;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlocklist jwtBlocklist;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CITIZEN_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID CASE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CATEGORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DEPARTMENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final String VALID_BODY = """
            {
              "subject": "Water leak",
              "description": "Leak on the main road",
              "type": "COMPLAINT",
              "priority": "HIGH",
              "channel": "PHONE",
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
        r.setCitizenId(CITIZEN_ID);
        r.setCitizenFullName("Citizen Test");
        return r;
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void createCaseForCitizen_returns201_asAgent() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(securityContextHelper.getAuthenticatedUsername()).thenReturn("agent01");
        when(caseService.createCitizenCase(eq(CITIZEN_ID), any(), eq(USER_ID)))
                .thenReturn(caseResponse());

        mockMvc.perform(post("/api/v1/citizens/" + CITIZEN_ID + "/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CASE_ID.toString()))
                .andExpect(jsonPath("$.caseNumber").value("CASE-2026-00001"))
                .andExpect(jsonPath("$.citizenId").value(CITIZEN_ID.toString()))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @WithMockUser(roles = "HANDLER")
    void createCaseForCitizen_returns201_asHandler() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(securityContextHelper.getAuthenticatedUsername()).thenReturn("handler01");
        when(caseService.createCitizenCase(eq(CITIZEN_ID), any(), eq(USER_ID)))
                .thenReturn(caseResponse());

        mockMvc.perform(post("/api/v1/citizens/" + CITIZEN_ID + "/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void createCaseForCitizen_returns404_whenCitizenUnknown() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(securityContextHelper.getAuthenticatedUsername()).thenReturn("agent01");
        when(caseService.createCitizenCase(eq(CITIZEN_ID), any(), eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("Citizen not found"));

        mockMvc.perform(post("/api/v1/citizens/" + CITIZEN_ID + "/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void createCaseForCitizen_returns400_whenSubjectMissing() throws Exception {
        String body = VALID_BODY.replace("\"subject\": \"Water leak\",", "");

        mockMvc.perform(post("/api/v1/citizens/" + CITIZEN_ID + "/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(caseService, never()).createCitizenCase(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void createCaseForCitizen_acceptsBodyWithoutNationalId() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(securityContextHelper.getAuthenticatedUsername()).thenReturn("agent01");
        when(caseService.createCitizenCase(eq(CITIZEN_ID), any(), eq(USER_ID)))
                .thenReturn(caseResponse());

        // The US-57 contract: no citizenNationalId in the body — the citizen
        // comes from the URL path, so masked PII (US-56) is never required.
        mockMvc.perform(post("/api/v1/citizens/" + CITIZEN_ID + "/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<CreateCitizenCaseRequest> captor =
                org.mockito.ArgumentCaptor.forClass(CreateCitizenCaseRequest.class);
        verify(caseService).createCitizenCase(eq(CITIZEN_ID), captor.capture(), eq(USER_ID));

        CreateCitizenCaseRequest sent = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(sent.getSubject()).isEqualTo("Water leak");
        org.assertj.core.api.Assertions.assertThat(sent.getPriority()).isEqualTo(Priority.HIGH);
        org.assertj.core.api.Assertions.assertThat(sent.getChannel()).isEqualTo(Channel.PHONE);
        org.assertj.core.api.Assertions.assertThat(sent.getAssignedToUserId()).isNull();
    }

    @Test
    void createCaseForCitizen_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/citizens/" + CITIZEN_ID + "/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void createCaseForCitizen_returns403_whenRoleInsufficient() throws Exception {
        mockMvc.perform(post("/api/v1/citizens/" + CITIZEN_ID + "/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verify(caseService, never()).createCitizenCase(any(), any(), any());
    }

    // ── US-58: possible-duplicate preflight ────────────────────────────

    @Test
    @WithMockUser(roles = "AGENT")
    void findDuplicateCandidates_returns200_withCandidates() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(securityContextHelper.getAuthenticatedUsername()).thenReturn("agent01");
        when(caseService.findDuplicateCandidates(eq(CITIZEN_ID), eq(CATEGORY_ID), eq(DEPARTMENT_ID), eq(USER_ID)))
                .thenReturn(List.of(new DuplicateCaseCandidateResponse(
                        CASE_ID, "CASE-2026-00010", "Water leak", CaseStatus.NEW, OffsetDateTime.now())));

        mockMvc.perform(get("/api/v1/citizens/" + CITIZEN_ID + "/cases/duplicate-candidates")
                        .param("categoryId", CATEGORY_ID.toString())
                        .param("departmentId", DEPARTMENT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(CASE_ID.toString()))
                .andExpect(jsonPath("$[0].caseNumber").value("CASE-2026-00010"))
                .andExpect(jsonPath("$[0].subject").value("Water leak"))
                .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void findDuplicateCandidates_returnsEmptyList_whenNoCandidates() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(securityContextHelper.getAuthenticatedUsername()).thenReturn("agent01");
        when(caseService.findDuplicateCandidates(any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/citizens/" + CITIZEN_ID + "/cases/duplicate-candidates")
                        .param("categoryId", CATEGORY_ID.toString())
                        .param("departmentId", DEPARTMENT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void findDuplicateCandidates_returns404_whenCitizenUnknown() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(securityContextHelper.getAuthenticatedUsername()).thenReturn("agent01");
        when(caseService.findDuplicateCandidates(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Citizen not found"));

        mockMvc.perform(get("/api/v1/citizens/" + CITIZEN_ID + "/cases/duplicate-candidates")
                        .param("categoryId", CATEGORY_ID.toString())
                        .param("departmentId", DEPARTMENT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void findDuplicateCandidates_returns400_whenParamsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/citizens/" + CITIZEN_ID + "/cases/duplicate-candidates"))
                .andExpect(status().isBadRequest());

        verify(caseService, never()).findDuplicateCandidates(any(), any(), any(), any());
    }

    @Test
    @WithMockUser
    void findDuplicateCandidates_returns403_whenRoleInsufficient() throws Exception {
        mockMvc.perform(get("/api/v1/citizens/" + CITIZEN_ID + "/cases/duplicate-candidates")
                        .param("categoryId", CATEGORY_ID.toString())
                        .param("departmentId", DEPARTMENT_ID.toString()))
                .andExpect(status().isForbidden());

        verify(caseService, never()).findDuplicateCandidates(any(), any(), any(), any());
    }

    // ── US-59: paged citizen case history ──────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCitizenCaseHistory_returns200_withPagedContent() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(securityContextHelper.getAuthenticatedUsername()).thenReturn("admin01");
        CaseSummaryResponse summary = CaseSummaryResponse.builder()
                .id(CASE_ID)
                .caseNumber("CASE-2026-00009")
                .subject("Water leak")
                .status("NEW")
                .priority("HIGH")
                .departmentNameEn("Housing")
                .departmentNameAr("الإسكان")
                .updatedAt(OffsetDateTime.now())
                .build();
        when(citizenService.getCitizenCaseHistory(eq(CITIZEN_ID), eq(USER_ID), eq(0), eq(20)))
                .thenReturn(new PagedResponse<>(List.of(summary), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/citizens/" + CITIZEN_ID + "/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(CASE_ID.toString()))
                .andExpect(jsonPath("$.content[0].caseNumber").value("CASE-2026-00009"))
                .andExpect(jsonPath("$.content[0].status").value("NEW"))
                .andExpect(jsonPath("$.content[0].departmentNameEn").value("Housing"))
                .andExpect(jsonPath("$.content[0].departmentNameAr").value("الإسكان"))
                .andExpect(jsonPath("$.content[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void getCitizenCaseHistory_forwardsPageAndSize() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(securityContextHelper.getAuthenticatedUsername()).thenReturn("agent01");
        when(citizenService.getCitizenCaseHistory(eq(CITIZEN_ID), eq(USER_ID), eq(2), eq(10)))
                .thenReturn(new PagedResponse<>(List.of(), 2, 10, 0, 0));

        mockMvc.perform(get("/api/v1/citizens/" + CITIZEN_ID + "/cases")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));

        verify(citizenService).getCitizenCaseHistory(CITIZEN_ID, USER_ID, 2, 10);
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void getCitizenCaseHistory_returns404_whenCitizenUnknown() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(securityContextHelper.getAuthenticatedUsername()).thenReturn("agent01");
        when(citizenService.getCitizenCaseHistory(any(), eq(USER_ID), anyInt(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Citizen not found"));

        mockMvc.perform(get("/api/v1/citizens/" + CITIZEN_ID + "/cases"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void getCitizenCaseHistory_returns400_whenPageNegativeOrSizeOutOfRange() throws Exception {
        mockMvc.perform(get("/api/v1/citizens/" + CITIZEN_ID + "/cases")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/citizens/" + CITIZEN_ID + "/cases")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(citizenService, never()).getCitizenCaseHistory(any(), any(), anyInt(), anyInt());
    }

    @Test
    @WithMockUser
    void getCitizenCaseHistory_returns403_whenRoleInsufficient() throws Exception {
        mockMvc.perform(get("/api/v1/citizens/" + CITIZEN_ID + "/cases"))
                .andExpect(status().isForbidden());

        verify(citizenService, never()).getCitizenCaseHistory(any(), any(), anyInt(), anyInt());
    }
}
