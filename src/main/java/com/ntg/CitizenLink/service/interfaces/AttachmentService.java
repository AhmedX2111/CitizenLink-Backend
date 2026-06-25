package com.ntg.CitizenLink.service.interfaces;

import com.ntg.CitizenLink.dto.agent.response.AttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface AttachmentService {

    /**
     * US-16: Upload an attachment to a case
     */
    AttachmentResponse uploadAttachment(UUID caseId, MultipartFile file, UUID uploadedByUserId);

    /**
     * Get all attachments for a case
     */
    List<AttachmentResponse> getAttachmentsByCaseId(UUID caseId);

    /**
     * Download an attachment by ID
     */
    Resource downloadAttachment(UUID attachmentId);

    /**
     * Get attachment by ID
     */
    AttachmentResponse getAttachmentById(UUID attachmentId);

    /**
     * Delete an attachment
     */
    void deleteAttachment(UUID attachmentId, UUID userId);

    /**
     * Count attachments for a case
     */
    long countAttachmentsByCaseId(UUID caseId);
}
