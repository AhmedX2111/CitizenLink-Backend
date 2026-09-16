package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.constants.DateRangeValidator;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.CaseRepository;
import com.ntg.citizenlink.service.interfaces.CsvExportService;
import com.ntg.citizenlink.util.MaskingContext;
import com.ntg.citizenlink.util.MaskingLevel;
import com.ntg.citizenlink.util.MaskingPolicy;
import com.ntg.citizenlink.util.PiiMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class CsvExportServiceImpl implements CsvExportService {

    private static final DateTimeFormatter CSV_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int PAGE_SIZE = 1000;
    private static final int DEFAULT_RANGE_DAYS = 30;

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
    public void exportCasesCsv(OutputStream out, OffsetDateTime startDate, OffsetDateTime endDate, UserRole requesterRole) throws IOException {
        OffsetDateTime start = startDate != null ? startDate : OffsetDateTime.now(ZoneOffset.UTC).minusDays(DEFAULT_RANGE_DAYS);
        OffsetDateTime end = endDate != null ? endDate : OffsetDateTime.now(ZoneOffset.UTC);
        DateRangeValidator.validate(start, end);

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        writer.write('\ufeff');
        writer.write(HEADER);
        writer.newLine();

        int pageNumber = 0;
        Slice<Case> slice;
        do {
            slice = caseRepository.findCasesForReportBetween(start, end, PageRequest.of(pageNumber, PAGE_SIZE));
            for (Case c : slice.getContent()) {
                writer.write(toCsvRow(c, requesterRole));
                writer.newLine();
            }
            writer.flush();
            pageNumber++;
        } while (slice.hasNext());
    }

    private String toCsvRow(Case c, UserRole requesterRole) {
        String nationalId = c.getCitizen() != null ? c.getCitizen().getNationalId() : "";
        String phone = c.getCitizen() != null ? c.getCitizen().getPhone() : "";
        String email = c.getCitizen() != null ? c.getCitizen().getEmail() : "";

        if (requesterRole != null) {
            MaskingContext ctx = MaskingContext.CSV_EXPORT;
            MaskingLevel nidLevel = MaskingPolicy.forField(requesterRole, "nationalId", ctx);
            if (nidLevel == MaskingLevel.MASKED) {
                nationalId = PiiMasker.maskNationalId(nationalId);
            }
            MaskingLevel phoneLevel = MaskingPolicy.forField(requesterRole, "phone", ctx);
            if (phoneLevel == MaskingLevel.MASKED) {
                phone = PiiMasker.maskPhone(phone);
            }
            MaskingLevel emailLevel = MaskingPolicy.forField(requesterRole, "email", ctx);
            if (emailLevel == MaskingLevel.MASKED) {
                email = PiiMasker.maskEmail(email);
            }
        }

        return String.join(",",
                csvEscape(c.getCaseNumber()),
                csvEscape(c.getSubject()),
                csvEscape(c.getDescription()),
                csvEscape(safeEnum(c.getType())),
                csvEscape(safeEnum(c.getPriority())),
                csvEscape(safeEnum(c.getStatus())),
                csvEscape(safeEnum(c.getChannel())),
                csvEscape(nationalId),
                csvEscape(c.getCitizen() != null ? c.getCitizen().getFullName() : ""),
                csvEscape(phone),
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
        String safe = neutralizeFormula(value);
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String neutralizeFormula(String value) {
        if (value.isEmpty()) return value;
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r') {
            return "'" + value;
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
