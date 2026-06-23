package com.ntg.CitizenLink.dto.agent.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private UUID id;
    private String code;
    private String nameEn;
    private String nameAr;
    private Boolean active;
    private Integer sortOrder;
}
