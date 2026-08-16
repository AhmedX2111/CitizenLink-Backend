package com.ntg.citizenlink.service;

import com.ntg.citizenlink.config.FileStorageProperties;
import com.ntg.citizenlink.support.CloseTrackingMultipartFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setUploadDir(tempDir.toString());
        properties.setMaxFileSize(5_242_880L);
        service = new FileStorageService(properties);
    }

    @Test
    void storeFile_closesSourceStream_andPersistsBytes() throws Exception {
        byte[] content = "hello attachment".getBytes(StandardCharsets.UTF_8);
        CloseTrackingMultipartFile file =
                new CloseTrackingMultipartFile("file", "note.txt", "text/plain", content);

        String storedName = service.storeFile(file);

        assertThat(storedName).endsWith(".txt");
        assertThat(file.isClosed()).isTrue();
        assertThat(Files.readAllBytes(tempDir.resolve(storedName))).isEqualTo(content);
    }

    @Test
    void storeFile_rejectsOversizedFile() {
        CloseTrackingMultipartFile file =
                new CloseTrackingMultipartFile("file", "big.pdf", "application/pdf", new byte[2048]);

        FileStorageProperties small = new FileStorageProperties();
        small.setUploadDir(tempDir.toString());
        small.setMaxFileSize(1024L);
        FileStorageService limited = new FileStorageService(small);

        assertThatThrownBy(() -> limited.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class);
    }
}