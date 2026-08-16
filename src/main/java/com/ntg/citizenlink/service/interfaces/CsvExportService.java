package com.ntg.citizenlink.service.interfaces;

import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;

public interface CsvExportService {

    /**
     * Streams the case-export CSV for the given createdAt range to {@code out}.
     * Dates are bounded server-side: absent dates default to the last 30 days
     * and the requested span may not exceed one year.
     */
    void exportCasesCsv(OutputStream out, OffsetDateTime startDate, OffsetDateTime endDate) throws IOException;
}
