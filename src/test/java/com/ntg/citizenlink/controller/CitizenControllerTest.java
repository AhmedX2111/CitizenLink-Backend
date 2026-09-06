package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.config.TestSecurityConfig;
import com.ntg.citizenlink.dto.agent.request.CreateCitizenCaseRequest;
import com.ntg.citizenlink.dto.agent.response.CaseResponse;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
}
