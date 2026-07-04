package com.ntg.CitizenLink.dto.agent.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response body for GET /api/v1/reports/volume (US-27, RPT-01, RPT-02, RPT-04).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolumeReportResponse {

    private List<DailyVolumeRow> dailyVolume;
    private List<CategoryCount> topCategories;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyVolumeRow {
        private String date;        // yyyy-MM-dd
        private long created;
        private long resolved;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryCount {
        private String categoryNameEn;
        private String categoryNameAr;
        private long count;
        private double percentage;
    }
}