package com.ntg.citizenlink.config;

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

    /**
     * M-13: whether the given (Tika-detected) MIME type is on the
     * {@code file.allowed-mime-types} whitelist. This config was previously
     * populated but never read anywhere.
     */
    public boolean isAllowedMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        for (String allowed : allowedMimeTypes) {
            if (allowed.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * M-13: whether the given extension (leading dot included, e.g. ".pdf") is
     * on the {@code file.allowed-extensions} whitelist. This config was
     * previously populated but never read anywhere.
     */
    public boolean isAllowedExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        for (String allowed : allowedExtensions) {
            if (allowed.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }
}
