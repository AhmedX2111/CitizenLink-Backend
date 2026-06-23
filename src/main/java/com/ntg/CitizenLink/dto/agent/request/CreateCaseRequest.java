package com.ntg.CitizenLink.dto.agent.request;

import com.ntg.CitizenLink.enums.CaseType;
import com.ntg.CitizenLink.enums.Channel;
import com.ntg.CitizenLink.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.ntg.CitizenLink.constants.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
}