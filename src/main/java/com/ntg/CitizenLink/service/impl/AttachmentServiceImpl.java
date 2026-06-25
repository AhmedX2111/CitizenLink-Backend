package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.GEH.ResourceNotFoundException;
import com.ntg.CitizenLink.config.FileStorageProperties;
import com.ntg.CitizenLink.dto.agent.response.AttachmentResponse;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.entities.Attachment;
import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import com.ntg.CitizenLink.repositories.AttachmentRepository;
import com.ntg.CitizenLink.repositories.CaseRepository;
import com.ntg.CitizenLink.service.FileStorageService;
import com.ntg.CitizenLink.service.interfaces.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final CaseRepository caseRepository;
    private final AppUserRepository appUserRepository;
    private final FileStorageService fileStorageService;
    private final FileStorageProperties fileStorageProperties;

    @Override
    @Transactional
    public AttachmentResponse uploadAttachment(UUID caseId, MultipartFile file, UUID uploadedByUserId) {
        log.info("Uploading attachment for case: {} by user: {}", caseId, uploadedByUserId);

        // Validate case exists
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Case", caseId));

        // Validate user exists
        AppUser uploadedBy = appUserRepository.findById(uploadedByUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", uploadedByUserId));

        try {
            // Store file on disk
            String storedFileName = fileStorageService.storeFile(file);
            String originalFileName = file.getOriginalFilename();
            String mimeType = file.getContentType();
            long fileSize = file.getSize();

            // Create attachment entity
            Attachment attachment = new Attachment();
            attachment.setCaseEntity(caseEntity);
            attachment.setOriginalFileName(originalFileName);
            attachment.setStoredFileName(storedFileName);
            attachment.setMimeType(mimeType != null ? mimeType : "application/octet-stream");
            attachment.setFileSizeBytes(fileSize);
            attachment.setStoragePath(fileStorageProperties.getUploadDir() + "/" + storedFileName);
            attachment.setUploadedByUser(uploadedBy);

            Attachment savedAttachment = attachmentRepository.save(attachment);

            log.info("Attachment uploaded successfully: {} (ID: {})", originalFileName, savedAttachment.getId());

            return toResponse(savedAttachment);

        } catch (Exception e) {
            log.error("Failed to upload attachment for case: {}", caseId, e);
            throw new RuntimeException("Failed to upload attachment: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachmentsByCaseId(UUID caseId) {
        log.debug("Fetching attachments for case: {}", caseId);

        if (!caseRepository.existsById(caseId)) {
            throw ResourceNotFoundException.of("Case", caseId);
        }

        List<Attachment> attachments = attachmentRepository.findByCaseEntityIdOrderByCreatedAtDesc(caseId);

        return attachments.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadAttachment(UUID attachmentId) {
        log.info("Downloading attachment: {}", attachmentId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Attachment", attachmentId));

        try {
            Path filePath = fileStorageService.loadFile(attachment.getStoredFileName());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                log.info("Attachment downloaded successfully: {}", attachment.getOriginalFileName());
                return resource;
            } else {
                log.error("File not found: {}", attachment.getStoredFileName());
                throw new RuntimeException("File not found: " + attachment.getOriginalFileName());
            }

        } catch (MalformedURLException e) {
            log.error("Failed to download attachment: {}", attachmentId, e);
            throw new RuntimeException("Failed to download attachment", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentResponse getAttachmentById(UUID attachmentId) {
        log.debug("Fetching attachment by ID: {}", attachmentId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Attachment", attachmentId));

        return toResponse(attachment);
    }

    @Override
    @Transactional
    public void deleteAttachment(UUID attachmentId, UUID userId) {
        log.info("Deleting attachment: {} by user: {}", attachmentId, userId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Attachment", attachmentId));

        // Check if user is the uploader or admin
        if (!attachment.getUploadedByUser().getId().equals(userId)) {
            AppUser user = appUserRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of("AppUser", userId));

            if (!"ADMIN".equals(user.getRole().name())) {
                throw new SecurityException("You are not authorized to delete this attachment");
            }
        }

        try {
            // Delete file from disk
            fileStorageService.deleteFile(attachment.getStoredFileName());

            // Delete record from database
            attachmentRepository.deleteById(attachmentId);

            log.info("Attachment deleted successfully: {}", attachmentId);

        } catch (Exception e) {
            log.error("Failed to delete attachment: {}", attachmentId, e);
            throw new RuntimeException("Failed to delete attachment", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countAttachmentsByCaseId(UUID caseId) {
        return attachmentRepository.countByCaseEntityId(caseId);
    }

    /**
     * Convert Attachment entity to AttachmentResponse DTO
     */
    private AttachmentResponse toResponse(Attachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .caseId(attachment.getCaseEntity().getId())
                .originalFileName(attachment.getOriginalFileName())
                .storedFileName(attachment.getStoredFileName())
                .mimeType(attachment.getMimeType())
                .fileSizeBytes(attachment.getFileSizeBytes())
                .fileSizeFormatted(fileStorageService.formatFileSize(attachment.getFileSizeBytes()))
                .uploadedByUserId(attachment.getUploadedByUser().getId())
                .uploadedByUserName(attachment.getUploadedByUser().getDisplayName())
                .uploadedByUserRole(attachment.getUploadedByUser().getRole().name())
                .createdAt(attachment.getCreatedAt())
                .build();
    }
}
