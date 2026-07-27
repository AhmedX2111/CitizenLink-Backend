package com.ntg.CitizenLink.dto.agent.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CitizenSearchRequest {
    private String searchTerm;  // Can be name (partial), national ID, or phone

    @Min(value = 0, message = "Page number must be 0 or greater")
    private int page = 0;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size must not exceed 100")
    private int size = 20;

    public boolean isEmpty() {
        return searchTerm == null || searchTerm.trim().isEmpty();
    }
}
