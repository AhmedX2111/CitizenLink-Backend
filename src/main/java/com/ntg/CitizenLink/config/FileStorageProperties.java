package com.ntg.CitizenLink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileStorageProperties {
    private String uploadDir = "./uploads";
    private long maxFileSize = 5_242_880; // 5 MB in bytes
    private String[] allowedMimeTypes = {
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    };
    private String[] allowedExtensions = {
            ".pdf", ".png", ".jpg", ".jpeg", ".docx"
    };
}
