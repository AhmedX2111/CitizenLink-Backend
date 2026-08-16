package com.ntg.citizenlink.dto.agent.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CreateCategoryRequest {

    @NotBlank(message = "English name is required")
    @Size(max = 200, message = "English name must not exceed 200 characters")
    private String nameEn;

    @NotBlank(message = "Arabic name is required")
    @Size(max = 200, message = "Arabic name must not exceed 200 characters")
    private String nameAr;

    private Boolean active;
}
