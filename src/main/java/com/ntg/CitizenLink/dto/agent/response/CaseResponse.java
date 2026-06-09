package com.ntg.CitizenLink.dto.agent.response;

import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.CaseType;
import com.ntg.CitizenLink.enums.Channel;
import com.ntg.CitizenLink.enums.Priority;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single case.
 *
 * Rules:
 *  - Never include storage_path (file system exposure).
 *  - Never include passwordHash (obviously).
 *  - Citizen and user references are flattened to id + display name only.
 *    Full citizen/user objects are fetched via their own endpoints.
 */
@Setter
@Getter
public class CaseResponse {

    private UUID id;
    private String caseNumber;
    private String subject;
    private String description;
    private CaseType type;
    private Priority priority;
    private CaseStatus status;
    private Channel channel;
    private String resolutionSummary;
    private OffsetDateTime dueAt;

    // Flattened citizen info
    private UUID citizenId;
    private String citizenFullName;
    private String citizenNationalId;

    // Flattened lookup refs
    private UUID categoryId;
    private String categoryNameEn;
    private String categoryNameAr;

    private UUID departmentId;
    private String departmentNameEn;
    private String departmentNameAr;

    // Flattened user refs
    private UUID createdByUserId;
    private String createdByDisplayName;

    private UUID assignedToUserId;
    private String assignedToDisplayName;

    // Timestamps
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime closedAt;
}
