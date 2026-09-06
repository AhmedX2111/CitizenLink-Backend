package com.ntg.citizenlink.dto.agent.request;

import com.ntg.citizenlink.enums.CaseType;
import com.ntg.citizenlink.enums.Channel;
import com.ntg.citizenlink.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * US-57: case creation launched from the Citizen 360 screen.
 *
 * Mirrors {@link CreateCaseRequest} but WITHOUT the citizen national ID —
 * the citizen is bound to the URL path by the caller instead. This lets
 * call-center agents create a case for the citizen they are already
 * looking at even when that citizen's national ID is masked for their
 * role (US-56), and guarantees the citizen cannot be silently swapped
 * during the flow.
 */
@Setter
@Getter
public class CreateCitizenCaseRequest {

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

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    @NotNull(message = "Department ID is required")
    private UUID departmentId;

    private UUID assignedToUserId;
    private OffsetDateTime dueAt;
}
