package com.ntg.citizenlink.dto.agent.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class NoteResponse {
    private UUID id;
    private UUID caseId;
    private UUID authorId;
    private String authorName;
    private String authorUsername;
    private String authorRole;
    private String body;
    private Boolean internal;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
