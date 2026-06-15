package com.ntg.CitizenLink.dto.agent.response;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitizenResponse {
    private UUID id;
    private String fullName;
    private String nationalId;
    private String phone;
    private String email;
    private String preferredLanguage;
    private OffsetDateTime createdAt;
    private Integer caseCount;  // Number of cases for this citizen
}
