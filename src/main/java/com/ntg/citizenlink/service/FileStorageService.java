package com.ntg.citizenlink.service;

import com.ntg.citizenlink.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageProperties fileStorageProperties;

    /**
     * Extension to use for each allowed (Tika-detected) MIME type. The stored
     * extension must always match the actual file content, never the
     * client-supplied original filename.
     */
    private static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            "application/pdf", ".pdf",
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"
    );

    private static final String OOXML_CONTAINER = "application/x-tika-ooxml";
    private static final String ZIP_MIME = "application/zip";
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * M-13: resolve a Tika-detected content MIME type to the canonical type used
     * for storage, enforcing the {@code file.allowed-mime-types} whitelist.
     *
     * <p>Tika reports the generic OOXML container type
     * {@code application/x-tika-ooxml} — and for some files a plain
     * {@code application/zip} — instead of the specific wordprocessingml type.
     * Such buckets are accepted ONLY when the client-declared extension is on
     * the {@code file.allowed-extensions} whitelist (today that means
     * {@code .docx}) AND the content actually looks like a Word document, and
     * are then canonicalized to the docx content type, so they are stored as
     * {@code <uuid>.docx}. A real {@code .zip} renamed to {@code .docx} is still
     * rejected because it contains no {@code word/document.xml} or
     * {@code [Content_Types].xml} referencing {@code wordprocessingml}.</p>
     *
     * @throws IllegalArgumentException when the detected type is not allowed
     */
    public String canonicalMimeType(String detectedMimeType, MultipartFile file) throws IOException {
        return canonicalMimeType(detectedMimeType,
                file.getOriginalFilename(), file.getInputStream());
    }

    /**
     * Pure variant for callers that already know the content cannot be ambiguous
     * (used by unit tests); {@code content} is {@code null}, so the ambiguous
     * {@code application/zip} bucket can never pass here.
     */
    public String canonicalMimeType(String detectedMimeType, String originalFileName) {
        try {
            return canonicalMimeType(detectedMimeType, originalFileName, null);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to inspect file content", e);
        }
    }

    private String canonicalMimeType(String detectedMimeType, String originalFileName,
                                     InputStream content) throws IOException {
        if (fileStorageProperties.isAllowedMimeType(detectedMimeType)) {
            return detectedMimeType;
        }
        boolean docxDeclared = ".docx".equalsIgnoreCase(extensionOf(originalFileName))
                && fileStorageProperties.isAllowedExtension(".docx");
        if (docxDeclared
                && (OOXML_CONTAINER.equals(detectedMimeType) || ZIP_MIME.equals(detectedMimeType))) {
            if (OOXML_CONTAINER.equals(detectedMimeType) || isWordOoxmlZip(content)) {
                return DOCX_MIME;
            }
        }
        throw new IllegalArgumentException("File type not allowed: " + detectedMimeType);
    }

    /**
     * Inspect an {@code application/zip}-detected stream for the structural
     * markers of a Microsoft Word OOXML document: a {@code word/document.xml}
     * entry, or a {@code [Content_Types].xml} entry referencing
     * {@code wordprocessingml}. Returns {@code false} (rejecting the upload) on
     * any non-docx zip or on zips whose content cannot be re-read.
     */
    private static boolean isWordOoxmlZip(InputStream content) throws IOException {
        if (content == null) {
            return false;
        }
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("word/document.xml".equals(name)) {
                    return true;
                }
                if ("[Content_Types].xml".equals(name)) {
                    byte[] head = new byte[8192];
                    int total = 0;
                    int read;
                    while (total < head.length && (read = zip.read(head, total, head.length - total)) != -1) {
                        total += read;
                    }
                    String snippet = new String(head, 0, total, StandardCharsets.UTF_8);
                    if (snippet.contains("wordprocessingml")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Lower-cased extension of the client-supplied filename (leading dot, e.g.
     * ".docx"), or {@code null} when there is none. Used ONLY as a fallback gate
     * for ambiguous content detection — never to build the stored path.
     */
    private static String extensionOf(String originalFileName) {
        if (originalFileName == null) {
            return null;
        }
        int lastSlash = Math.max(originalFileName.lastIndexOf('/'), originalFileName.lastIndexOf('\\'));
        String base = lastSlash >= 0 ? originalFileName.substring(lastSlash + 1) : originalFileName;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(dot).toLowerCase(Locale.ROOT) : null;
    }

    /**
     * Store a file on disk and return the stored file name.
     *
     * @param file            the uploaded multipart file (content + original name)
     * @param detectedMimeType the MIME type detected from the file content (Tika);
     *                         the client-controlled original filename is never used
     *                         to build the stored path
     */
    public String storeFile(MultipartFile file, String detectedMimeType) throws IOException {
        // Validate file (size, emptiness)
        validateFile(file);

        // M-13: never build the stored name from the client-controlled original
        // filename. The extension is derived from the content-detected MIME type
        // and checked against the configured file.allowed-extensions whitelist
        // (which was populated but never read), so a PDF cannot be persisted as
        // <uuid>.html or <uuid>.svg and nothing outside the allowed set is written.
        String extension = EXTENSION_BY_MIME.get(detectedMimeType);
        if (extension == null || !fileStorageProperties.isAllowedExtension(extension)) {
            throw new IllegalArgumentException("File type not allowed: " + detectedMimeType);
        }

        // Create upload directory if it doesn't exist
        Path uploadDir = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
            log.info("Created upload directory: {}", uploadDir);
        }

        // Generate unique file name from a random UUID + the validated extension
        String storedFileName = UUID.randomUUID().toString() + extension;

        // M-13: containment guard — the same normalize() + startsWith(uploadDir)
        // check that loadFile/deleteFile perform. The stored name is already
        // attacker-independent (UUID + validated extension), but asserting the
        // resolved path stays under the upload directory makes a
        // write-outside-base-dir impossible even if a future caller changes the
        // name construction.
        Path targetPath = uploadDir.resolve(storedFileName).normalize();
        if (!targetPath.startsWith(uploadDir)) {
            throw new SecurityException("Path traversal attempt detected");
        }

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("File stored successfully: {} -> {}", file.getOriginalFilename(), storedFileName);

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
