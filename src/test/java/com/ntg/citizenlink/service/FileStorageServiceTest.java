package com.ntg.citizenlink.service;

import com.ntg.citizenlink.config.FileStorageProperties;
import com.ntg.citizenlink.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        FileStorageProperties props = new FileStorageProperties();
        props.setUploadDir(tempDir.toString());
        props.setAllowedMimeTypes(new String[]{"application/pdf", "image/png"});
        props.setAllowedExtensions(new String[]{".pdf", ".png"});
        fileStorageService = new FileStorageService(props);
    }

    private Path uploadRoot() {
        return tempDir.toAbsolutePath().normalize();
    }

    @Test
    void storeFile_derivesExtensionFromDetectedMime_ignoringClientFilename() throws Exception {
        // PDF content submitted under a misleading .html name (M-13 regression).
        byte[] content = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.html", "application/pdf", content);

        String stored = fileStorageService.storeFile(file, "application/pdf");

        assertThat(stored).endsWith(".pdf");
        assertThat(stored).doesNotContain("html");
        Path storedPath = uploadRoot().resolve(stored).normalize();
        assertThat(storedPath.startsWith(uploadRoot())).isTrue();
        assertThat(Files.readAllBytes(storedPath)).isEqualTo(content);
    }

    @Test
    void storeFile_withUnmappedMimeType_throws() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.bin", "application/octet-stream", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> fileStorageService.storeFile(file, "application/octet-stream"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("File type not allowed");
        assertThat(Files.list(uploadRoot())).isEmpty();
    }

    @Test
    void storeFile_withValidMimeButNotWhitelistedExtension_throws() throws Exception {
        // The generated extension (.jpg) is not in the configured
        // file.allowed-extensions whitelist (only .pdf/.png here) — M-13.
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> fileStorageService.storeFile(file, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("File type not allowed");
        assertThat(Files.list(uploadRoot())).isEmpty();
    }

    @Test
    void storeFile_withTraversalClientFilename_cannotEscapeUploadDir() throws Exception {
        // Even a malicious client filename with ../ segments never influences the
        // stored path: the name is a UUID + the validated extension (M-13).
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/passwd.sh", "application/pdf", "%PDF".getBytes());

        String stored = fileStorageService.storeFile(file, "application/pdf");

        assertThat(stored).endsWith(".pdf");
        assertThat(stored).doesNotContain("..", "/", "\\");
        Path storedPath = uploadRoot().resolve(stored).normalize();
        assertThat(storedPath.startsWith(uploadRoot())).isTrue();
        assertThat(Files.exists(storedPath)).isTrue();
    }

    private FileStorageService docxStorageService() {
        FileStorageProperties props = new FileStorageProperties();
        props.setUploadDir(tempDir.toString());
        props.setAllowedMimeTypes(new String[]{
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"});
        props.setAllowedExtensions(new String[]{".docx"});
        return new FileStorageService(props);
    }

    @Test
    void canonicalMimeType_allowsAlreadyCanonicalDocxType() {
        String canonical = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        String result = docxStorageService().canonicalMimeType(canonical, "whatever.exe");

        assertThat(result).isEqualTo(canonical);
    }

    @Test
    void canonicalMimeType_tikaOoxmlWithDocxClientExtension_canonicalizesToDocx() throws Exception {
        // Tika reports application/x-tika-ooxml for some valid .docx files; the
        // client-declared .docx extension (which is itself whitelisted) resolves
        // the ambiguity so the file is stored as a proper docx.
        String canonical = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        FileStorageService service = docxStorageService();

        String mime = service.canonicalMimeType("application/x-tika-ooxml", "_مراجعة مشروع CitizenLink .docx");
        assertThat(mime).isEqualTo(canonical);

        MockMultipartFile file = new MockMultipartFile(
                "file", "_مراجعة مشروع CitizenLink .docx", mime, new byte[]{1, 2, 3});
        String stored = service.storeFile(file, mime);

        assertThat(stored).endsWith(".docx");
        assertThat(Files.exists(uploadRoot().resolve(stored))).isTrue();
    }

    @Test
    void canonicalMimeType_tikaOoxmlWithoutWhitelistedClientExtension_rejected() {
        // x-tika-ooxml with an extension that is NOT on the whitelist must not
        // bypass the MIME whitelist (e.g. .xlsx, .pptx are not admitted).
        assertThatThrownBy(() -> docxStorageService()
                .canonicalMimeType("application/x-tika-ooxml", "spreadsheet.xlsx"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("File type not allowed");
    }

    @Test
    void canonicalMimeType_unknownTypeWithoutExtension_rejected() {
        assertThatThrownBy(() -> docxStorageService()
                .canonicalMimeType("application/zip", "archive.zip"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("File type not allowed");
    }

    private static byte[] docxLikeZip(String contentTypesXml, String documentXml) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            if (contentTypesXml != null) {
                zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zip.write(contentTypesXml.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            if (documentXml != null) {
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                zip.write(documentXml.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void canonicalMimeType_zipWithDocxClientNameAndWordContent_canonicalizesToDocx() throws Exception {
        // Tika reports some valid .docx files as application/zip; the content is
        // independently verified as a Word OOXML document before acceptance.
        String canonical = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        byte[] content = docxLikeZip(
                null,
                "<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>");
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.docx", "application/zip", content);

        String mime = docxStorageService().canonicalMimeType("application/zip", file);

        assertThat(mime).isEqualTo(canonical);
        String stored = docxStorageService().storeFile(file, mime);
        assertThat(stored).endsWith(".docx");
        assertThat(Files.readAllBytes(uploadRoot().resolve(stored))).isEqualTo(content);
    }

    @Test
    void canonicalMimeType_zipWithContentTypesReference_canonicalizesToDocx() throws Exception {
        // A docx whose only identifying marker is [Content_Types].xml.
        String canonical = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        byte[] content = docxLikeZip(
                "<?xml version=\"1.0\"?><Types><Default Extension=\"xml\" "
                        + "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
                null);
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.docx", "application/zip", content);

        String mime = docxStorageService().canonicalMimeType("application/zip", file);

        assertThat(mime).isEqualTo(canonical);
    }

    @Test
    void canonicalMimeType_zipWithDocxClientNameButPlainZipContent_rejected() throws Exception {
        // A real zip renamed .docx must be rejected: no Word structural markers.
        byte[] content = docxLikeZip(null, null);
        MockMultipartFile file = new MockMultipartFile(
                "file", "archive.docx", "application/zip", content);

        assertThatThrownBy(() -> docxStorageService().canonicalMimeType("application/zip", file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("File type not allowed");
        assertThat(Files.list(uploadRoot())).isEmpty();
    }
}