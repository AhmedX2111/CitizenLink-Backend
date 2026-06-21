package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.response.DashboardSummaryResponse;
import com.ntg.CitizenLink.dto.agent.response.MyOpenCaseResponse;
import com.ntg.CitizenLink.security.config.SecurityContextHelper;
import com.ntg.CitizenLink.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Dashboard endpoints backing US-04, US-05, US-06.
 *
 *   GET /api/v1/dashboard/summary      — KPI cards + status chart (any authenticated user)
 *   GET /api/v1/dashboard/my-open-cases — HANDLER-only widget (US-06)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * US-04 + US-05.
     * Accessible to any authenticated role — KPI cards and the status chart
     * are relevant to AGENT, HANDLER, SUPERVISOR, and ADMIN alike.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('AGENT', 'HANDLER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    /**
     * US-06. Restricted to HANDLER only per acceptance criteria.
     * "assignedToUser = current user" — the current authenticated HANDLER.
     */
    @GetMapping("/my-open-cases")
    @PreAuthorize("hasRole('HANDLER')")
    public ResponseEntity<List<MyOpenCaseResponse>> getMyOpenCases() {
        UUID userId = securityContextHelper.getAuthenticatedUserId();
        return ResponseEntity.ok(dashboardService.getMyOpenCases(userId));
    }
}