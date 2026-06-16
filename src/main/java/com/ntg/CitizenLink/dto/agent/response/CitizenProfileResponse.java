package com.ntg.CitizenLink.dto.agent.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CitizenProfileResponse {
    // Citizen details
    private UUID id;
    private String fullName;
    private String nationalId;
    private String phone;
    private String email;
    private String preferredLanguage;
    private OffsetDateTime createdAt;
    private String createdByUserName;

    // Statistics
    private int totalCases;
    private int openCases;
    private int resolvedCases;

    // Case history
    private List<CaseSummaryResponse> recentCases;
}

