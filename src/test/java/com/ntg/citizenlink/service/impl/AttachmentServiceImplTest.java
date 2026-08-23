package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.config.FileStorageProperties;
import com.ntg.citizenlink.dto.agent.response.AttachmentResponse;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Attachment;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.entities.Category;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.entities.Department;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.AttachmentRepository;
import com.ntg.citizenlink.repositories.CaseRepository;
import com.ntg.citizenlink.security.CaseAccessPolicy;
import com.ntg.citizenlink.service.FileStorageService;
import com.ntg.citizenlink.support.CloseTrackingMultipartFile;
import com.ntg.citizenlink.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private FileStorageProperties fileStorageProperties;
    @Mock private CaseAccessPolicy caseAccessPolicy;

    private AttachmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AttachmentServiceImpl(attachmentRepository, caseRepository, appUserRepository,
                fileStorageService, fileStorageProperties, caseAccessPolicy);
    }

    @Test
    void uploadAttachment_closesMimeDetectionStream() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AppUser creator = EntityFactory.appUser(UserRole.AGENT);
        Case caseEntity = EntityFactory.aCase(
                EntityFactory.citizen(creator),
                EntityFactory.category(),
                EntityFactory.department(),
                creator);
        AppUser uploader = EntityFactory.appUser(UserRole.HANDLER);

        byte[] pdf = "%PDF-1.4 hello".getBytes(StandardCharsets.UTF_8);
        CloseTrackingMultipartFile file =
                new CloseTrackingMultipartFile("file", "doc.pdf", "application/pdf", pdf);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(uploader));
        when(caseAccessPolicy.canView(any(), any())).thenReturn(true);
        when(fileStorageService.storeFile(any())).thenReturn("stored-uuid.pdf");
        when(attachmentRepository.save(any())).thenAnswer(invocation -> {
            Attachment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AttachmentResponse response = service.uploadAttachment(caseId, file, userId);

        assertThat(response.getMimeType()).isEqualTo("application/pdf");
        assertThat(response.getOriginalFileName()).isEqualTo("doc.pdf");
        assertThat(file.isClosed()).isTrue();
    }

    @Test
    void uploadAttachment_rejectsDisallowedMimeType_beforeStoring() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AppUser creator = EntityFactory.appUser(UserRole.AGENT);
        Case caseEntity = EntityFactory.aCase(
                EntityFactory.citizen(creator),
                EntityFactory.category(),
                EntityFactory.department(),
                creator);
        AppUser uploader = EntityFactory.appUser(UserRole.HANDLER);

        byte[] exe = new byte[]{(byte) 0x4D, (byte) 0x5A, 0x00, 0x01, 0x02};
        CloseTrackingMultipartFile file =
                new CloseTrackingMultipartFile("file", "tool.exe", "application/octet-stream", exe);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(uploader));
        when(caseAccessPolicy.canView(any(), any())).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.uploadAttachment(caseId, file, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File type not allowed");

        assertThat(file.isClosed()).isTrue();
    }
}