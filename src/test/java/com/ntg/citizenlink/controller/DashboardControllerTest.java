package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.config.TestSecurityConfig;
import com.ntg.citizenlink.dto.agent.response.WorkloadIndicatorsResponse;
import com.ntg.citizenlink.security.JwtBlocklist;
import com.ntg.citizenlink.security.config.SecurityContextHelper;
import com.ntg.citizenlink.service.interfaces.DashboardService;
import com.ntg.citizenlink.service.interfaces.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-54: role access and payload shape for GET /api/v1/dashboard/workload.
 * HANDLER, SUPERVISOR, and ADMIN may call it (AC: handler, supervisor, admin);
 * AGENT/unauthenticated are rejected. The service is mocked —
 * counting correctness is covered in DashboardServiceImplTest and
 * WorkloadIndicatorsIntegrationTest.
 */
@WebMvcTest(DashboardController.class)
@Import(TestSecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private SecurityContextHelper securityContextHelper;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlocklist jwtBlocklist;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private WorkloadIndicatorsResponse personalResponse() {
        return new WorkloadIndicatorsResponse(
                WorkloadIndicatorsResponse.Scope.PERSONAL,
                List.of(
                        new WorkloadIndicatorsResponse.Indicator(
                                WorkloadIndicatorsResponse.Key.ASSIGNED, 12L,
                                "/api/v1/dashboard/my-inbox"),
                        new WorkloadIndicatorsResponse.Indicator(
                                WorkloadIndicatorsResponse.Key.OVERDUE, 4L,
                                "/api/v1/dashboard/my-inbox?overdue=true"),
                        new WorkloadIndicatorsResponse.Indicator(
                                WorkloadIndicatorsResponse.Key.DUE_TODAY, 2L,
                                "/api/v1/dashboard/my-inbox?dueToday=true")));
    }

    private WorkloadIndicatorsResponse teamResponse() {
        return new WorkloadIndicatorsResponse(
                WorkloadIndicatorsResponse.Scope.TEAM,
                List.of(
                        new WorkloadIndicatorsResponse.Indicator(
                                WorkloadIndicatorsResponse.Key.OVERDUE, 5L,
                                "/api/v1/cases?overdue=true"),
                        new WorkloadIndicatorsResponse.Indicator(
                                WorkloadIndicatorsResponse.Key.DUE_TODAY, 3L,
                                "/api/v1/cases?dueToday=true"),
                        new WorkloadIndicatorsResponse.Indicator(
                                WorkloadIndicatorsResponse.Key.UNASSIGNED, 1L,
                                "/api/v1/cases?unassigned=true")));
    }

    @Test
    @WithMockUser(roles = "HANDLER")
    void workload_returns200_withPersonalIndicators_asHandler() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(dashboardService.getWorkloadIndicators(USER_ID)).thenReturn(personalResponse());

        mockMvc.perform(get("/api/v1/dashboard/workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("PERSONAL"))
                .andExpect(jsonPath("$.indicators.length()").value(3))
                .andExpect(jsonPath("$.indicators[0].key").value("ASSIGNED"))
                .andExpect(jsonPath("$.indicators[0].count").value(12))
                .andExpect(jsonPath("$.indicators[1].key").value("OVERDUE"))
                .andExpect(jsonPath("$.indicators[1].link").value("/api/v1/dashboard/my-inbox?overdue=true"))
                .andExpect(jsonPath("$.indicators[2].key").value("DUE_TODAY"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void workload_returns200_withTeamIndicators_asSupervisor() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(dashboardService.getWorkloadIndicators(USER_ID)).thenReturn(teamResponse());

        mockMvc.perform(get("/api/v1/dashboard/workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("TEAM"))
                .andExpect(jsonPath("$.indicators.length()").value(3))
                .andExpect(jsonPath("$.indicators[0].key").value("OVERDUE"))
                .andExpect(jsonPath("$.indicators[0].link").value("/api/v1/cases?overdue=true"))
                .andExpect(jsonPath("$.indicators[1].key").value("DUE_TODAY"))
                .andExpect(jsonPath("$.indicators[2].key").value("UNASSIGNED"))
                .andExpect(jsonPath("$.indicators[2].count").value(1));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void workload_returns403_asAgent() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/workload"))
                .andExpect(status().isForbidden());

        verify(dashboardService, never()).getWorkloadIndicators(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void workload_returns200_withTeamIndicators_asAdmin() throws Exception {
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(dashboardService.getWorkloadIndicators(USER_ID)).thenReturn(teamResponse());

        mockMvc.perform(get("/api/v1/dashboard/workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("TEAM"))
                .andExpect(jsonPath("$.indicators.length()").value(3))
                .andExpect(jsonPath("$.indicators[0].key").value("OVERDUE"))
                .andExpect(jsonPath("$.indicators[0].link").value("/api/v1/cases?overdue=true"))
                .andExpect(jsonPath("$.indicators[1].key").value("DUE_TODAY"))
                .andExpect(jsonPath("$.indicators[2].key").value("UNASSIGNED"))
                .andExpect(jsonPath("$.indicators[2].count").value(1));
    }

    @Test
    void workload_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/workload"))
                .andExpect(status().isUnauthorized());
    }
}
