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
import java.util.Arrays;
import java.util.List;
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
        // Check if file is empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Check file size
        long maxSize = fileStorageProperties.getMaxFileSize();
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed size of %d MB",
                            maxSize / (1024 * 1024))
            );
        }

        // Check file type
        String mimeType = file.getContentType();
        String originalFileName = file.getOriginalFilename();

        // Check MIME type
        List<String> allowedMimeTypes = Arrays.asList(fileStorageProperties.getAllowedMimeTypes());
        if (mimeType == null || !allowedMimeTypes.contains(mimeType)) {
            // Check by extension as fallback
            if (originalFileName == null || !isAllowedExtension(originalFileName)) {
                throw new IllegalArgumentException(
                        String.format("File type not allowed. Allowed types: %s",
                                String.join(", ", allowedMimeTypes))
                );
            }
        }

        // Check by extension
        if (originalFileName == null || !isAllowedExtension(originalFileName)) {
            throw new IllegalArgumentException(
                    String.format("File type not allowed. Allowed extensions: %s",
                            String.join(", ", fileStorageProperties.getAllowedExtensions()))
            );
        }

        log.debug("File validated: {} (MIME: {}, Size: {} bytes)",
                originalFileName, mimeType, file.getSize());
    }

    /**
     * Check if file extension is allowed
     */
    private boolean isAllowedExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerFileName = fileName.toLowerCase();
        return Arrays.stream(fileStorageProperties.getAllowedExtensions())
                .anyMatch(lowerFileName::endsWith);
    }

    /**
     * Load file from disk
     */
    public Path loadFile(String storedFileName) {
        Path uploadPath = Paths.get(fileStorageProperties.getUploadDir());
        Path filePath = uploadPath.resolve(storedFileName);

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + storedFileName);
        }

        return filePath;
    }

    /**
     * Delete file from disk
     */
    public void deleteFile(String storedFileName) throws IOException {
        Path uploadPath = Paths.get(fileStorageProperties.getUploadDir());
        Path filePath = uploadPath.resolve(storedFileName);

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
