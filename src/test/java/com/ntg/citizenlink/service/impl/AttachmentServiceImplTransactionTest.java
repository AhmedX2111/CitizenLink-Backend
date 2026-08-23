package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.config.FileStorageProperties;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Attachment;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.AttachmentRepository;
import com.ntg.citizenlink.repositories.CaseRepository;
import com.ntg.citizenlink.security.CaseAccessPolicy;
import com.ntg.citizenlink.service.FileStorageService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * M-14: disk writes and Database writes are not atomic in either direction.
 *
 * <p>Proves, against a real (H2-backed) transaction so the
 * {@code TransactionSynchronization} callbacks actually fire:</p>
 *
 * <ul>
 *   <li>upload commits  → stored file is kept</li>
 *   <li>upload rolls back → stored file is cleaned up (no orphan on disk)</li>
 *   <li>delete commits  → stored file is removed after the row delete</li>
 *   <li>delete rolls back → stored file is kept (no row whose file is gone)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTransactionTest {

    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final byte[] PDF_BYTES = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private CaseAccessPolicy caseAccessPolicy;

    private AttachmentServiceImpl service;
    private TransactionTemplate txTemplate;
    private Path uploadRoot;
    private FileStorageProperties fileStorageProperties;
    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageProperties = new FileStorageProperties();
        fileStorageProperties.setUploadDir(tempDir.toString());
        fileStorageProperties.setAllowedMimeTypes(new String[]{"application/pdf"});
        fileStorageProperties.setAllowedExtensions(new String[]{".pdf"});
        fileStorageService = new FileStorageService(fileStorageProperties);
        uploadRoot = tempDir.toAbsolutePath().normalize();

        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:m14;DB_CLOSE_DELAY=-1");
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        txTemplate = new TransactionTemplate(txManager);

        service = new AttachmentServiceImpl(
                attachmentRepository, caseRepository, appUserRepository,
                fileStorageService, fileStorageProperties, caseAccessPolicy);
    }

    private void stubCaseAndUserAccess() {
        Case caseEntity = new Case();
        caseEntity.setId(CASE_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(caseEntity));
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(caseAccessPolicy.canView(any(), any())).thenReturn(true);
    }

    private void stubSaveAssignsId() {
        when(attachmentRepository.save(any())).thenAnswer(invocation -> {
            Attachment attachment = invocation.getArgument(0);
            attachment.setId(UUID.randomUUID());
            return attachment;
        });
    }

    private static AppUser user(UUID id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(UserRole.HANDLER);
        user.setDisplayName("Handler");
        return user;
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "report.pdf", "application/pdf", PDF_BYTES);
    }

    private List<Path> filesInUploadDir() throws Exception {
        try (var stream = Files.list(uploadRoot)) {
            return stream.toList();
        }
    }

    @Test
    void uploadWithCommittedTransaction_keepsStoredFile() throws Exception {
        stubCaseAndUserAccess();
        stubSaveAssignsId();

        txTemplate.executeWithoutResult(status ->
                service.uploadAttachment(CASE_ID, pdf(), USER_ID));

        assertThat(filesInUploadDir()).hasSize(1);
    }

    @Test
    void uploadWithRolledBackTransaction_removesStoredFile() throws Exception {
        stubCaseAndUserAccess();
        stubSaveAssignsId();

        txTemplate.executeWithoutResult(status -> {
            service.uploadAttachment(CASE_ID, pdf(), USER_ID);
            status.setRollbackOnly();
        });

        assertThat(filesInUploadDir()).isEmpty();
    }

    private Attachment attachmentWithFileOnDisk() throws Exception {
        String storedFileName = fileStorageService.storeFile(pdf(), "application/pdf");
        assertThat(Files.exists(uploadRoot.resolve(storedFileName))).isTrue();

        Attachment attachment = new Attachment();
        attachment.setId(UUID.randomUUID());
        Case caseEntity = new Case();
        caseEntity.setId(CASE_ID);
        attachment.setCaseEntity(caseEntity);
        attachment.setStoredFileName(storedFileName);
        attachment.setUploadedByUser(user(USER_ID));
        return attachment;
    }

    @Test
    void deleteWithCommittedTransaction_removesStoredFile() throws Exception {
        Attachment attachment = attachmentWithFileOnDisk();
        when(attachmentRepository.findById(attachment.getId())).thenReturn(Optional.of(attachment));
        stubCaseAndUserAccess();

        txTemplate.executeWithoutResult(status ->
                service.deleteAttachment(CASE_ID, attachment.getId(), USER_ID));

        assertThat(filesInUploadDir()).isEmpty();
    }

    @Test
    void deleteWithRolledBackTransaction_keepsStoredFile() throws Exception {
        Attachment attachment = attachmentWithFileOnDisk();
        when(attachmentRepository.findById(attachment.getId())).thenReturn(Optional.of(attachment));
        stubCaseAndUserAccess();
        assertThat(filesInUploadDir()).hasSize(1);

        txTemplate.executeWithoutResult(status -> {
            service.deleteAttachment(CASE_ID, attachment.getId(), USER_ID);
            status.setRollbackOnly();
        });

        assertThat(filesInUploadDir()).hasSize(1);
    }
}