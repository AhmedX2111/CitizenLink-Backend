package com.ntg.citizenlink.dto.agent.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class AttachmentResponse {
    private UUID id;
    private UUID caseId;
    private String originalFileName;
    private String storedFileName;
    private String mimeType;
    private Long fileSizeBytes;
    private String fileSizeFormatted;
    private UUID uploadedByUserId;
    private String uploadedByUserName;
    private String uploadedByUserRole;
    private OffsetDateTime createdAt;
}
