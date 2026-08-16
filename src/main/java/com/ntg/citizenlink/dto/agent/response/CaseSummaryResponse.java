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
}
