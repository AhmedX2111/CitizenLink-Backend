package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.repositories.CaseRepository;
import com.ntg.CitizenLink.service.interfaces.CsvExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CsvExportServiceImpl implements CsvExportService {

    private static final DateTimeFormatter CSV_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String HEADER = String.join(",",
            "Case Number",
            "Subject",
            "Description",
            "Type",
            "Priority",
            "Status",
            "Channel",
            "Citizen National ID",
            "Citizen Full Name",
            "Citizen Phone",
            "Category",
            "Department",
            "Created By",
            "Assigned To",
            "Due At",
            "Created At",
            "Resolved At",
            "Closed At",
            "Resolution Summary"
    );

    private final CaseRepository caseRepository;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCasesCsv(OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Case> cases;
        if (startDate != null && endDate != null) {
            cases = caseRepository.findCasesForReportBetween(startDate, endDate);
        } else if (startDate != null) {
            cases = caseRepository.findCasesForReportAfter(startDate);
        } else if (endDate != null) {
            cases = caseRepository.findCasesForReportBefore(endDate);
        } else {
            cases = caseRepository.findAllCasesForReport();
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8));

        writer.write('\ufeff');
        writer.println(HEADER);

        for (Case c : cases) {
            writer.println(toCsvRow(c));
        }

        writer.flush();
        return bos.toByteArray();
    }

    private String toCsvRow(Case c) {
        return String.join(",",
                csvEscape(c.getCaseNumber()),
                csvEscape(c.getSubject()),
                csvEscape(c.getDescription()),
                csvEscape(safeEnum(c.getType())),
                csvEscape(safeEnum(c.getPriority())),
                csvEscape(safeEnum(c.getStatus())),
                csvEscape(safeEnum(c.getChannel())),
                csvEscape(c.getCitizen() != null ? c.getCitizen().getNationalId() : ""),
                csvEscape(c.getCitizen() != null ? c.getCitizen().getFullName() : ""),
                csvEscape(c.getCitizen() != null ? c.getCitizen().getPhone() : ""),
                csvEscape(c.getCategory() != null ? c.getCategory().getNameEn() : ""),
                csvEscape(c.getDepartment() != null ? c.getDepartment().getNameEn() : ""),
                csvEscape(c.getCreatedByUser() != null ? c.getCreatedByUser().getDisplayName() : ""),
                csvEscape(c.getAssignedToUser() != null ? c.getAssignedToUser().getDisplayName() : ""),
                csvEscape(formatDt(c.getDueAt())),
                csvEscape(formatDt(c.getCreatedAt())),
                csvEscape(formatDt(c.getResolvedAt())),
                csvEscape(formatDt(c.getClosedAt())),
                csvEscape(c.getResolutionSummary())
        );
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String formatDt(OffsetDateTime dt) {
        return dt != null ? dt.format(CSV_DT) : "";
    }

    private static String safeEnum(Enum<?> e) {
        return e != null ? e.name() : "";
    }
}
