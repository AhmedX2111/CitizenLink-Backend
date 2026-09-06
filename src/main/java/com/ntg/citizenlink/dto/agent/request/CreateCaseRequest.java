package com.ntg.citizenlink.dto.agent.request;

import com.ntg.citizenlink.enums.CaseType;
import com.ntg.citizenlink.enums.Channel;
import com.ntg.citizenlink.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.ntg.citizenlink.constants.ValidationPatterns;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Setter
@Getter
public class CreateCaseRequest {

    @NotBlank(message = "Subject is required")
    @Size(max = 255, message = "Subject must not exceed 255 characters")
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @NotNull(message = "Case type is required")
    private CaseType type;

    @NotNull(message = "Priority is required")
    private Priority priority;

    @NotNull(message = "Channel is required")
    private Channel channel;

    @NotBlank(message = "Citizen National ID is required")
    @Pattern(regexp = ValidationPatterns.NATIONAL_ID_PATTERN, message = ValidationPatterns.NATIONAL_ID_MESSAGE)
    private String citizenNationalId;

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    @NotNull(message = "Department ID is required")
    private UUID departmentId;

    private UUID assignedToUserId;
    private OffsetDateTime dueAt;

    /**
     * Optional — US-58: the agent's short reason for creating this case
     * despite an active possible-duplicate warning. Never required on the
     * server side (the client makes it mandatory after a warning was
     * shown); only the length cap is enforced here.
     */
    @Size(max = 500, message = "Duplicate reason must not exceed 500 characters")
    private String duplicateReason;
}