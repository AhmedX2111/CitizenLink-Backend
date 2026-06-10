package com.ntg.CitizenLink.dto.agent.request;

import com.ntg.CitizenLink.enums.CaseType;
import com.ntg.CitizenLink.enums.Channel;
import com.ntg.CitizenLink.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request body for POST /api/v1/cases.
 *
 * SECURITY NOTE: `status` is intentionally absent from this DTO.
 * Status is always set to NEW by CaseService and must never be
 * accepted from the request body. See ERD design risk: "Status written
 * directly from API body — CRITICAL".
 */
@Setter
@Getter
public class CreateCaseRequest {

    @NotBlank(message = "Subject is required")
    @Size(max = 255, message = "Subject must not exceed 255 characters")
    private String subject;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Case type is required")
    private CaseType type;

    @NotNull(message = "Priority is required")
    private Priority priority;

    @NotNull(message = "Channel is required")
    private Channel channel;

    @NotNull(message = "Citizen ID is required")
    private UUID citizenId;

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    @NotNull(message = "Department ID is required")
    private UUID departmentId;

    /** Optional at creation. Supervisor assigns later via workflow. */
    private UUID assignedToUserId;

    /** Optional SLA deadline. */
    private OffsetDateTime dueAt;
}
