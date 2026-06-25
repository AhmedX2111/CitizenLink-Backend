package com.ntg.CitizenLink.dto.agent.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddNoteRequest {

    @NotBlank(message = "Note body is required")
    @Size(min = 1, max = 5000, message = "Note must be between 1 and 5000 characters")
    private String body;

    private Boolean internal = true;  // Default to internal
}
