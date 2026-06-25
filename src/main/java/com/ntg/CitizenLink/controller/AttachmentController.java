package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.response.AttachmentResponse;
import com.ntg.CitizenLink.security.config.SecurityContextHelper;
import com.ntg.CitizenLink.service.interfaces.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/cases/{caseId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * US-16: Upload an attachment to a case
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @PathVariable UUID caseId,
            @RequestParam("file") MultipartFile file
    ) {
        log.info("REST request: POST /api/v1/cases/{}/attachments - filename: {}, size: {} bytes",
                caseId, file.getOriginalFilename(), file.getSize());

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        AttachmentResponse response = attachmentService.uploadAttachment(caseId, file, userId);

        log.info("REST response: POST /api/v1/cases/{}/attachments - status: 201 CREATED", caseId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * US-16: Get all attachments for a case
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<List<AttachmentResponse>> getAttachmentsByCaseId(
            @PathVariable UUID caseId
    ) {
        log.info("REST request: GET /api/v1/cases/{}/attachments", caseId);

        List<AttachmentResponse> responses = attachmentService.getAttachmentsByCaseId(caseId);

        log.info("REST response: GET /api/v1/cases/{}/attachments - found {} attachments", caseId, responses.size());
        return ResponseEntity.ok(responses);
    }

    /**
     * US-16: Download an attachment
     */
    @GetMapping("/{attachmentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable UUID caseId,
            @PathVariable UUID attachmentId
    ) {
        log.info("REST request: GET /api/v1/cases/{}/attachments/{}/download", caseId, attachmentId);

        // Get attachment metadata first
        AttachmentResponse attachment = attachmentService.getAttachmentById(attachmentId);

        // Download the file
        Resource resource = attachmentService.downloadAttachment(attachmentId);

        // Set headers for download
        String encodedFileName = URLEncoder.encode(attachment.getOriginalFileName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(attachment.getMimeType()));
        headers.setContentLength(attachment.getFileSizeBytes());
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + attachment.getOriginalFileName() +
                        "\"; filename*=UTF-8''" + encodedFileName);

        log.info("REST response: GET /api/v1/cases/{}/attachments/{}/download - file downloaded", caseId, attachmentId);

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    /**
     * Get attachment metadata
     */
    @GetMapping("/{attachmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<AttachmentResponse> getAttachmentById(
            @PathVariable UUID caseId,
            @PathVariable UUID attachmentId
    ) {
        log.info("REST request: GET /api/v1/cases/{}/attachments/{}", caseId, attachmentId);

        AttachmentResponse response = attachmentService.getAttachmentById(attachmentId);

        log.info("REST response: GET /api/v1/cases/{}/attachments/{} - attachment found", caseId, attachmentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete an attachment
     */
    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable UUID caseId,
            @PathVariable UUID attachmentId
    ) {
        log.info("REST request: DELETE /api/v1/cases/{}/attachments/{}", caseId, attachmentId);

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        attachmentService.deleteAttachment(attachmentId, userId);

        log.info("REST response: DELETE /api/v1/cases/{}/attachments/{} - status: 204 NO CONTENT", caseId, attachmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Count attachments for a case
     */
    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<Long> countAttachmentsByCaseId(
            @PathVariable UUID caseId
    ) {
        log.info("REST request: GET /api/v1/cases/{}/attachments/count", caseId);

        long count = attachmentService.countAttachmentsByCaseId(caseId);

        log.info("REST response: GET /api/v1/cases/{}/attachments/count - {}", caseId, count);
        return ResponseEntity.ok(count);
    }
}
