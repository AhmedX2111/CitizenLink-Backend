package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.config.TestSecurityConfig;
import com.ntg.citizenlink.dto.agent.response.DepartmentResponse;
import com.ntg.citizenlink.security.JwtBlocklist;
import com.ntg.citizenlink.security.config.SecurityContextHelper;
import com.ntg.citizenlink.service.interfaces.DepartmentService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L-12: the {@code active} flag is required on department create, mirroring the
 * update DTO — an omitting request must be rejected at the boundary instead of
 * silently defaulting to the entity default.
 */
@WebMvcTest(DepartmentController.class)
@Import(TestSecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartmentService departmentService;

    @MockitoBean
    private SecurityContextHelper securityContextHelper;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlocklist jwtBlocklist;

    @Test
    void create_withoutActive_returns400_validationError() throws Exception {
        mockMvc.perform(post("/api/v1/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nameEn\":\"Operations\",\"nameAr\":\"\u0627\u0644\u0639\u0645\u0644\u064a\u0627\u062a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("active"))
                .andExpect(jsonPath("$.details[0].message").value("Active flag is required"));

        verify(departmentService, never()).createDepartment(any());
    }

    @Test
    void create_withActive_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        DepartmentResponse response = DepartmentResponse.builder()
                .id(id)
                .code("DP-001")
                .nameEn("Operations")
                .nameAr("\u0627\u0644\u0639\u0645\u0644\u064a\u0627\u062a")
                .active(true)
                .build();
        when(departmentService.createDepartment(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nameEn\":\"Operations\",\"nameAr\":\"\u0627\u0644\u0639\u0645\u0644\u064a\u0627\u062a\",\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value("DP-001"))
                .andExpect(jsonPath("$.active").value(true));
    }
}
