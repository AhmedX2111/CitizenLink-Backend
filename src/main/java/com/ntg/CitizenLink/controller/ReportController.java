package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.response.VolumeReportResponse;
import com.ntg.CitizenLink.service.ReportService;
import com.ntg.CitizenLink.service.interfaces.CsvExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService volumeReportService;
    private final CsvExportService csvExportService;

    /**
     * US-27, RPT-01/RPT-02/RPT-04: daily volume report with top categories.
     */
    @GetMapping("/volume")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<VolumeReportResponse> getVolumeReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        VolumeReportResponse response = volumeReportService.getVolumeReport(from, to);
        return ResponseEntity.ok(response);
    }

    /**
     * US-28, RPT-03: export cases as CSV, optionally filtered by date range.
     */
    @GetMapping("/export/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        OffsetDateTime start = null;
        OffsetDateTime end = null;
        if (startDate != null) {
            start = startDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        }
        if (endDate != null) {
            end = endDate.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toOffsetDateTime();
        }

        byte[] csv = csvExportService.exportCasesCsv(start, end);

        String filename = "citizenlink-cases-"
                + LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(csv.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(csv);
    }
}
