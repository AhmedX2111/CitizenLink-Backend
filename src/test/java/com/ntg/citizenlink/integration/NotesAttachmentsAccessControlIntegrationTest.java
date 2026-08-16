package com.ntg.citizenlink.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Category;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.entities.Department;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.AppUserRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotesAttachmentsAccessControlIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";
    private static final byte[] PDF_BYTES = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppUser owner;
    private AppUser intruder;
    private AppUser admin;
    private AppUser supervisor;
    private AppUser handler;
    private Citizen citizen;
    private Category category;
    private Department department;
    private String ownerToken;
    private String intruderToken;
    private String adminToken;
    private String supervisorToken;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser(UserRole.AGENT);
        intruder = createUser(UserRole.AGENT);
        admin = createUser(UserRole.ADMIN);
        supervisor = createUser(UserRole.SUPERVISOR);
        handler = createUser(UserRole.HANDLER);
        citizen = EntityFactory.citizen(owner);
        citizen.setNationalId(uniqueNationalId());
        citizenRepository.save(citizen);
        category = categoryRepository.save(EntityFactory.category());
        department = departmentRepository.save(EntityFactory.department());
        ownerToken = login(owner.getUsername());
        intruderToken = login(intruder.getUsername());
        adminToken = login(admin.getUsername());
        supervisorToken = login(supervisor.getUsername());
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
                                  "subject": "Access control case",
                                  "description": "IDOR test fixture",
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

    private void assignToHandler(String caseId) throws Exception {
        mockMvc.perform(post("/api/v1/cases/{id}/transition", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\",\"assignedToUserId\":\"" + handler.getId() + "\"}"))
                .andExpect(status().isOk());
    }

    private String addNote(String token, String caseId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cases/{caseId}/notes", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Access control note\",\"internal\":true}"))
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
    // Attachments - M-04 validation failures must not become 500
    // -------------------------------------------------------------------------

    @Test
    void disallowedFileType_returns400_withUsableMessage() throws Exception {
        String caseId = createCase(ownerToken);
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "hello world".getBytes());

        mockMvc.perform(multipart("/api/v1/cases/{caseId}/attachments", caseId)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("File type not allowed")));
    }

    @Test
    void emptyFile_returns400_not500() throws Exception {
        String caseId = createCase(ownerToken);
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/cases/{caseId}/attachments", caseId)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // -------------------------------------------------------------------------
    // Notes - access control
    // -------------------------------------------------------------------------

    @Test
    void owner_canListAndGetOwnNotes() throws Exception {
        String caseId = createCase(ownerToken);
        String noteId = addNote(ownerToken, caseId);

        mockMvc.perform(get("/api/v1/cases/{caseId}/notes", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(noteId));

        mockMvc.perform(get("/api/v1/cases/{caseId}/notes/{noteId}", caseId, noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(noteId));
    }

    @Test
    void owner_canAddNoteToOwnCase() throws Exception {
        String caseId = createCase(ownerToken);
        addNote(ownerToken, caseId);
    }

    @Test
    void admin_canListAndGetAnyCaseNotes() throws Exception {
        String caseId = createCase(ownerToken);
        String noteId = addNote(ownerToken, caseId);

        mockMvc.perform(get("/api/v1/cases/{caseId}/notes", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(noteId));

        mockMvc.perform(get("/api/v1/cases/{caseId}/notes/{noteId}", caseId, noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(noteId));
    }

    @Test
    void intruder_cannotListNotes_onAnotherUsersCase() throws Exception {
        String caseId = createCase(ownerToken);

        mockMvc.perform(get("/api/v1/cases/{caseId}/notes", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void intruder_cannotGetNoteById_onAnotherUsersCase() throws Exception {
        String caseId = createCase(ownerToken);
        String noteId = addNote(ownerToken, caseId);

        mockMvc.perform(get("/api/v1/cases/{caseId}/notes/{noteId}", caseId, noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void intruder_cannotAddNote_onAnotherUsersCase() throws Exception {
        String caseId = createCase(ownerToken);

        mockMvc.perform(post("/api/v1/cases/{caseId}/notes", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"intruder note\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void intruder_cannotUpdateNote_onAnotherUsersCase() throws Exception {
        String caseId = createCase(ownerToken);
        String noteId = addNote(ownerToken, caseId);

        mockMvc.perform(put("/api/v1/cases/{caseId}/notes/{noteId}", caseId, noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"tampered note\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void intruder_cannotDeleteNote_onAnotherUsersCase() throws Exception {
        String caseId = createCase(ownerToken);
        String noteId = addNote(ownerToken, caseId);

        mockMvc.perform(delete("/api/v1/cases/{caseId}/notes/{noteId}", caseId, noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticated_cannotDeleteNote_returnsUnauthorized() throws Exception {
        String caseId = createCase(ownerToken);
        String noteId = addNote(ownerToken, caseId);

        mockMvc.perform(delete("/api/v1/cases/{caseId}/notes/{noteId}", caseId, noteId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handler_assignedToCase_cannotUpdateAuthorsNote_returns403() throws Exception {
        String caseId = createCase(ownerToken);
        assignToHandler(caseId);
        String noteId = addNote(ownerToken, caseId);
        String handlerToken = login(handler.getUsername());

        mockMvc.perform(put("/api/v1/cases/{caseId}/notes/{noteId}", caseId, noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"handler tampered\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void handler_assignedToCase_cannotDeleteAuthorsNote_returns403() throws Exception {
        String caseId = createCase(ownerToken);
        assignToHandler(caseId);
        String noteId = addNote(ownerToken, caseId);
        String handlerToken = login(handler.getUsername());

        mockMvc.perform(delete("/api/v1/cases/{caseId}/notes/{noteId}", caseId, noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getNonExistentNote_returns404() throws Exception {
        String caseId = createCase(ownerToken);
        String missingId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/cases/{caseId}/notes/{noteId}", caseId, missingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void noteId_fromOtherCase_withTamperedCaseId_returns404() throws Exception {
        String ownerCaseId = createCase(ownerToken);
        String noteId = addNote(ownerToken, ownerCaseId);
        String otherCaseId = createCase(intruderToken);

        mockMvc.perform(get("/api/v1/cases/{caseId}/notes/{noteId}", otherCaseId, noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidNoteIdFormat_returns400() throws Exception {
        String caseId = createCase(ownerToken);

        mockMvc.perform(get("/api/v1/cases/{caseId}/notes/{noteId}", caseId, "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCaseIdFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/cases/{caseId}/notes", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wildcardCaseId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/cases/{caseId}/notes", "***")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Attachments - access control
    // -------------------------------------------------------------------------

    @Test
    void owner_canListAndGetOwnAttachments() throws Exception {
        String caseId = createCase(ownerToken);
        String attachmentId = uploadAttachment(ownerToken, caseId);

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attachmentId));

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments/{attachmentId}", caseId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(attachmentId));
    }

    @Test
    void owner_canDownloadOwnAttachment() throws Exception {
        String caseId = createCase(ownerToken);
        String attachmentId = uploadAttachment(ownerToken, caseId);

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments/{attachmentId}/download", caseId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void admin_canDownloadAnyCaseAttachment() throws Exception {
        String caseId = createCase(ownerToken);
        String attachmentId = uploadAttachment(ownerToken, caseId);

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments/{attachmentId}/download", caseId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void intruder_cannotListAttachments_onAnotherUsersCase() throws Exception {
        String caseId = createCase(ownerToken);

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments", caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void intruder_cannotDownloadAttachment_onAnotherUsersCase() throws Exception {
        String caseId = createCase(ownerToken);
        String attachmentId = uploadAttachment(ownerToken, caseId);

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments/{attachmentId}/download", caseId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void intruder_cannotUploadAttachment_onAnotherUsersCase() throws Exception {
        String caseId = createCase(ownerToken);
        MockMultipartFile file = new MockMultipartFile(
                "file", "intruder.pdf", MediaType.APPLICATION_PDF_VALUE, PDF_BYTES);

        mockMvc.perform(multipart("/api/v1/cases/{caseId}/attachments", caseId)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void intruder_cannotDeleteAttachment_onAnotherUsersCase() throws Exception {
        String caseId = createCase(ownerToken);
        String attachmentId = uploadAttachment(ownerToken, caseId);

        mockMvc.perform(delete("/api/v1/cases/{caseId}/attachments/{attachmentId}", caseId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void handler_assignedToCase_cannotDeleteUploadersAttachment_returns403() throws Exception {
        String caseId = createCase(ownerToken);
        assignToHandler(caseId);
        String attachmentId = uploadAttachment(ownerToken, caseId);
        String handlerToken = login(handler.getUsername());

        mockMvc.perform(delete("/api/v1/cases/{caseId}/attachments/{attachmentId}", caseId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + handlerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getNonExistentAttachment_returns404() throws Exception {
        String caseId = createCase(ownerToken);
        String missingId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments/{attachmentId}", caseId, missingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void attachmentId_fromOtherCase_withTamperedCaseId_returns404() throws Exception {
        String ownerCaseId = createCase(ownerToken);
        String attachmentId = uploadAttachment(ownerToken, ownerCaseId);
        String otherCaseId = createCase(intruderToken);

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments/{attachmentId}", otherCaseId, attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidAttachmentIdFormat_returns400() throws Exception {
        String caseId = createCase(ownerToken);

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments/{attachmentId}", caseId, "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wildcardAttachmentId_returns400() throws Exception {
        String caseId = createCase(ownerToken);

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments/{attachmentId}", caseId, "***")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }
}
