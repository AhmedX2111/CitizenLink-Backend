package com.ntg.citizenlink.dto.agent.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class CaseSummaryResponse {
    private UUID id;
    private String caseNumber;
    private String subject;
    private String status;
    private String priority;
    private OffsetDateTime createdAt;
    private String assignedToName;

    /**
     * US-59: assigned department, both languages so the caller can render the
     * name in the active UI language (mirrors CaseResponse.departmentNameEn/Ar).
     */
    private String departmentNameEn;
    private String departmentNameAr;

    /**
     * US-59: last update timestamp — the recent-cases list is ordered by this.
     */
    private OffsetDateTime updatedAt;
}
