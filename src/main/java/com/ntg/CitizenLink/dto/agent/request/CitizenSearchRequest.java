package com.ntg.CitizenLink.dto.agent.request;

import lombok.Data;

@Data
public class CitizenSearchRequest {
    private String searchTerm;  // Can be name (partial), national ID, or phone
    private Integer page = 0;
    private Integer size = 20;

    public boolean isEmpty() {
        return searchTerm == null || searchTerm.trim().isEmpty();
    }
}
