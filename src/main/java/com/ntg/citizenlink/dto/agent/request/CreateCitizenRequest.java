package com.ntg.citizenlink.dto.agent.request;

import jakarta.validation.constraints.NotBlank;
import com.ntg.citizenlink.constants.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.ntg.citizenlink.constants.ValidationPatterns;
import lombok.Data;

@Data
public class CreateCitizenRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 200, message = "Full name must be between 2 and 200 characters")
    private String fullName;

    @NotBlank(message = "National ID is required")
    @Pattern(regexp = ValidationPatterns.NATIONAL_ID_PATTERN, message = ValidationPatterns.NATIONAL_ID_MESSAGE)
    private String nationalId;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{11}$", message = "Phone number must be 11 digits")
    private String phone;

    @Pattern(regexp = "^$|^[A-Za-z0-9+_.-]+@(.+)$", message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^(en|ar)$", message = "Preferred language must be 'en' or 'ar'")
    private String preferredLanguage = "en";
}
