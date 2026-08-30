package com.ntg.citizenlink.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Category;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.entities.Department;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.AttachmentRepository;
import com.ntg.citizenlink.repositories.CategoryRepository;
import com.ntg.citizenlink.repositories.CitizenRepository;
import com.ntg.citizenlink.repositories.DepartmentRepository;
import com.ntg.citizenlink.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-46: uploaded files must be validated securely before any permanent
 * storage — size, content type and authentication are all enforced, and each
 * failure surfaces as a readable 4xx error envelope instead of a 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "file.max-file-size=1048576")
class UploadValidationIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";
    private static final byte[] PDF_BYTES = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF"
            .getBytes(StandardCharsets.UTF_8);

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AttachmentRepository attachmentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppUser owner;
    private Citizen citizen;
    private Category category;
    private Department department;
    private String ownerToken;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser(UserRole.AGENT);
        citizen = EntityFactory.citizen(owner);
        citizen.setNationalId(uniqueNationalId());
        citizenRepository.save(citizen);
        category = categoryRepository.save(EntityFactory.category());
        department = departmentRepository.save(EntityFactory.department());
        ownerToken = login(owner.getUsername());
    }

    private static String uniqueNationalId() {
        return String.format("%016d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000_000_000L));
    }

    private AppUser createUser(UserRole role) {
        AppUser user = EntityFactory.appUser(role);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String createCase(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "Upload validation case",
                                  "description": "US-46 test fixture",
                                  "type": "COMPLAINT",
                                  "priority": "HIGH",
                                  "channel": "WEB",
                                  "citizenNationalId": "%s",
                                  "categoryId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(citizen.getNationalId(), category.getId(), department.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String uploadAttachment(String token, String caseId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", MediaType.APPLICATION_PDF_VALUE, PDF_BYTES);
        MvcResult result = mockMvc.perform(multipart("/api/v1/cases/{caseId}/attachments", caseId)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // -------------------------------------------------------------------------
    // US-46 - size validation
    // -------------------------------------------------------------------------

    @Test
    void oversizeUpload_returns400ReadableError_andStoresNothing() throws Exception {
        // AC2: a file above file.max-file-size (1 MB via @TestPropertySource) is
        // rejected with a readable 400 BEFORE anything is persisted.
        String caseId = createCase(ownerToken);

        byte[] content = new byte[1_048_577]; // 1 byte over the 1 MB test limit
        System.arraycopy(PDF_BYTES, 0, content, 0, PDF_BYTES.length);
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.pdf", MediaType.APPLICATION_PDF_VALUE, content);

        mockMvc.perform(multipart("/api/v1/cases/{caseId}/attachments", caseId)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("File size exceeds")));

        // No attachment row was created. (The upload directory is shared by all
        // integration tests, so the DB is the robust place to assert that
        // nothing was stored.)
        assertThat(attachmentRepository.findByCaseEntityIdOrderByCreatedAtDesc(UUID.fromString(caseId))).isEmpty();
    }

    // -------------------------------------------------------------------------
    // US-46 - content type validation
    // -------------------------------------------------------------------------

    @Test
    void executableFileType_returns400ReadableError() throws Exception {
        // AC4/AC6: an executable (MZ magic bytes) is not on the MIME whitelist
        // and must be rejected with the standardized readable error envelope.
        String caseId = createCase(ownerToken);
        MockMultipartFile file = new MockMultipartFile(
                "file", "tool.exe", "application/octet-stream",
                new byte[]{(byte) 0x4D, (byte) 0x5A, 0x00, 0x01, 0x02});

        mockMvc.perform(multipart("/api/v1/cases/{caseId}/attachments", caseId)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("File type not allowed")));
    }

    // -------------------------------------------------------------------------
    // US-46 - download requires authentication
    // -------------------------------------------------------------------------

    @Test
    void unauthenticatedDownload_returns401() throws Exception {
        // AC5: downloading an attachment requires authentication; a request
        // without credentials is rejected with 401 before any file access.
        String caseId = createCase(ownerToken);
        String attachmentId = uploadAttachment(ownerToken, caseId);

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments/{attachmentId}/download", caseId, attachmentId))
                .andExpect(status().isUnauthorized());
    }
}
