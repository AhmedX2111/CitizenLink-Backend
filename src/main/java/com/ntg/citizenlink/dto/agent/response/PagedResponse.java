package com.ntg.citizenlink.dto.agent.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Generic paginated response envelope used by all list endpoints.
 *
 * Example JSON:
 * {
 *   "content": [...],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 87,
 *   "totalPages": 5,
 *   "first": true,
 *   "last": false
 * }
 */
@Setter
@Getter
public class PagedResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public PagedResponse() {}

    public PagedResponse(List<T> content, int page, int size,
                         long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.first = page == 0;
        this.last = page >= totalPages - 1;
    }
}
