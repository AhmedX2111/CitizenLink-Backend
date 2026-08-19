package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.exception.ResourceNotFoundException;
import com.ntg.citizenlink.config.FileStorageProperties;
import com.ntg.citizenlink.dto.agent.response.AttachmentResponse;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.entities.Attachment;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.repositories.AppUserRepository;
import com.ntg.citizenlink.repositories.AttachmentRepository;
import com.ntg.citizenlink.repositories.CaseRepository;
import com.ntg.citizenlink.security.CaseAccessPolicy;
import com.ntg.citizenlink.service.FileStorageService;
import com.ntg.citizenlink.service.interfaces.AttachmentService;
import org.apache.tika.Tika;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final CaseAccessPolicy caseAccessPolicy;

    @Override
    @Transactional
    public AttachmentResponse uploadAttachment(UUID caseId, MultipartFile file, UUID uploadedByUserId) {
        log.info("Uploading attachment for case: {} by user: {}", caseId, uploadedByUserId);

        Case caseEntity = requireAccessibleCase(caseId, uploadedByUserId);

        AppUser uploadedBy = appUserRepository.findById(uploadedByUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", uploadedByUserId));

        try {
            // Detect actual MIME type from file content (magic bytes)
            Tika tika = new Tika();
            String detectedMimeType = tika.detect(file.getInputStream());

            // M-13: resolve/canonicalize the detected type against the whitelist
            // (also handling Tika's generic application/x-tika-ooxml and
            // application/zip detections of valid .docx files).
            String mimeType = fileStorageService.canonicalMimeType(detectedMimeType, file);

            // Store file on disk (extension derived from the canonical MIME type,
            // never from the client-controlled original filename — M-13)
            String storedFileName = fileStorageService.storeFile(file, mimeType);
            String originalFileName = file.getOriginalFilename();
            long fileSize = file.getSize();

            // Create attachment entity
            Attachment attachment = new Attachment();
            attachment.setCaseEntity(caseEntity);
            attachment.setOriginalFileName(originalFileName);
            attachment.setStoredFileName(storedFileName);
            attachment.setMimeType(mimeType);
            attachment.setFileSizeBytes(fileSize);
            attachment.setStoragePath(fileStorageProperties.getUploadDir() + "/" + storedFileName);
            attachment.setUploadedByUser(uploadedBy);

            Attachment savedAttachment = attachmentRepository.save(attachment);

            // M-14: the file lands on disk inside the transaction, but the DB row
            // is only durable at commit. If the transaction rolls back (commit
            // failure, or any exception after the copy), remove the file so it is
            // not orphaned with no row to point at it.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                                    try {
                                        fileStorageService.deleteFile(storedFileName);
                                        log.info("Cleaned up stored file after rollback: {}", storedFileName);
                                    } catch (IOException e) {
                                        log.error("Failed to clean up stored file after rollback: {}", storedFileName, e);
                                    }
                                }
                            }
                        });
            } else {
                log.warn("No active transaction for upload; cannot register rollback cleanup for {}", storedFileName);
            }

            log.info("Attachment uploaded successfully: {} (ID: {})", originalFileName, savedAttachment.getId());

            return toResponse(savedAttachment);

        } catch (IOException e) {
            log.error("Failed to upload attachment for case: {}", caseId, e);
            throw new RuntimeException("Failed to upload attachment: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachmentsByCaseId(UUID caseId, UUID requesterId) {
        log.debug("Fetching attachments for case: {}", caseId);

        requireAccessibleCase(caseId, requesterId);

        List<Attachment> attachments = attachmentRepository.findByCaseEntityIdOrderByCreatedAtDesc(caseId);

        return attachments.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadAttachment(UUID caseId, UUID attachmentId, UUID requesterId) {
        log.info("Downloading attachment: {} from case: {}", attachmentId, caseId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Attachment", attachmentId));

        if (!attachment.getCaseEntity().getId().equals(caseId)) {
            throw ResourceNotFoundException.of("Attachment", attachmentId);
        }

        requireAccessibleCase(caseId, requesterId);

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
    public AttachmentResponse getAttachmentById(UUID caseId, UUID attachmentId, UUID requesterId) {
        log.debug("Fetching attachment by ID: {} in case: {}", attachmentId, caseId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Attachment", attachmentId));

        if (!attachment.getCaseEntity().getId().equals(caseId)) {
            throw ResourceNotFoundException.of("Attachment", attachmentId);
        }

        requireAccessibleCase(caseId, requesterId);

        return toResponse(attachment);
    }

    @Override
    @Transactional
    public void deleteAttachment(UUID caseId, UUID attachmentId, UUID userId) {
        log.info("Deleting attachment: {} from case: {} by user: {}", attachmentId, caseId, userId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Attachment", attachmentId));

        if (!attachment.getCaseEntity().getId().equals(caseId)) {
            throw ResourceNotFoundException.of("Attachment", attachmentId);
        }

        requireAccessibleCase(caseId, userId);

        // Check if user is the uploader or admin
        if (!attachment.getUploadedByUser().getId().equals(userId)) {
            AppUser user = appUserRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of("AppUser", userId));

            if (!"ADMIN".equals(user.getRole().name())) {
                throw new SecurityException("You are not authorized to delete this attachment");
            }
        }

        String storedFileName = attachment.getStoredFileName();

        // M-14: reverse the previous order (which deleted the file before the
        // row). The DB delete happens inside the transaction and the disk
        // delete is deferred until the commit succeeds — so a rollback leaves
        // the file intact instead of a row whose storedFileName no longer
        // exists on disk (every later download would 500).
        attachmentRepository.deleteById(attachmentId);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                fileStorageService.deleteFile(storedFileName);
                                log.info("Removed file after committed delete: {}", storedFileName);
                            } catch (IOException e) {
                                log.error("Failed to delete file after commit (orphaned): {}", storedFileName, e);
                            }
                        }
                    });
        } else {
            try {
                fileStorageService.deleteFile(storedFileName);
            } catch (IOException e) {
                log.error("Failed to delete file (no active transaction): {}", storedFileName, e);
            }
        }

        log.info("Attachment deleted successfully: {}", attachmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAttachmentsByCaseId(UUID caseId, UUID requesterId) {
        requireAccessibleCase(caseId, requesterId);
        return attachmentRepository.countByCaseEntityId(caseId);
    }

    private Case requireAccessibleCase(UUID caseId, UUID requesterId) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Case", caseId));
        AppUser requester = appUserRepository.findById(requesterId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", requesterId));
        if (!caseAccessPolicy.canView(caseEntity, requester)) {
            log.warn("User {} attempted to access case {} without permission", requesterId, caseId);
            throw ResourceNotFoundException.of("Case", caseId);
        }
        return caseEntity;
    }

    /**
     * Convert Attachment entity to AttachmentResponse DTO
     */
    private AttachmentResponse toResponse(Attachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .caseId(attachment.getCaseEntity().getId())
                .originalFileName(attachment.getOriginalFileName())
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
