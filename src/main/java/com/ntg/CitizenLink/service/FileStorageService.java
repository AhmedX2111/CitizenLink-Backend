package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageProperties fileStorageProperties;

    /**
     * Store a file on disk and return the stored file name
     */
    public String storeFile(MultipartFile file) throws IOException {
        // Validate file
        validateFile(file);

        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(fileStorageProperties.getUploadDir());
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("Created upload directory: {}", uploadPath.toAbsolutePath());
        }

        // Generate unique file name
        String originalFileName = file.getOriginalFilename();
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String storedFileName = UUID.randomUUID().toString() + extension;

        // Copy file to upload directory
        Path targetPath = uploadPath.resolve(storedFileName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("File stored successfully: {} -> {}", originalFileName, storedFileName);

        return storedFileName;
    }

    /**
     * Validate file before storing
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        long maxSize = fileStorageProperties.getMaxFileSize();
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed size of %d MB",
                            maxSize / (1024 * 1024))
            );
        }
    }

    /**
     * Load file from disk with path traversal protection.
     */
    public Path loadFile(String storedFileName) {
        Path uploadDir = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();
        Path filePath = uploadDir.resolve(storedFileName).normalize();

        if (!filePath.startsWith(uploadDir)) {
            throw new SecurityException("Path traversal attempt detected");
        }

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + storedFileName);
        }

        return filePath;
    }

    /**
     * Delete file from disk with path traversal protection.
     */
    public void deleteFile(String storedFileName) throws IOException {
        Path uploadDir = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();
        Path filePath = uploadDir.resolve(storedFileName).normalize();

        if (!filePath.startsWith(uploadDir)) {
            throw new SecurityException("Path traversal attempt detected");
        }

        if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.info("File deleted: {}", storedFileName);
        }
    }

    /**
     * Format file size for display
     */
    public String formatFileSize(long sizeInBytes) {
        if (sizeInBytes < 1024) {
            return sizeInBytes + " B";
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeInBytes / 1024.0);
        } else {
            return String.format("%.1f MB", sizeInBytes / (1024.0 * 1024));
        }
    }
}
