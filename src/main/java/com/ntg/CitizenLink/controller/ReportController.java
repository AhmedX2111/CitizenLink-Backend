package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.response.VolumeReportResponse;
import com.ntg.CitizenLink.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * US-27 (RPT-01, RPT-02, RPT-04).
 * Accessible to SUPERVISOR and ADMIN only (RPT-01 access rule).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * GET /api/v1/reports/volume?from=2026-01-01&to=2026-01-31
     *
     * Defaults: last 7 days if parameters are absent.
     */
    @GetMapping("/volume")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<VolumeReportResponse> getVolumeReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (to == null)   to   = LocalDate.now();
        if (from == null) from = to.minusDays(6);

        log.info("GET /api/v1/reports/volume from={} to={}", from, to);

        return ResponseEntity.ok(reportService.getVolumeReport(from, to));
    }
}