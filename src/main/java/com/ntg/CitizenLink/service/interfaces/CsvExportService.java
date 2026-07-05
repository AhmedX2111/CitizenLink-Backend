package com.ntg.CitizenLink.service.interfaces;

import java.time.OffsetDateTime;

public interface CsvExportService {

    byte[] exportCasesCsv(OffsetDateTime startDate, OffsetDateTime endDate);
}
