package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.request.AddNoteRequest;
import com.ntg.CitizenLink.dto.agent.response.NoteResponse;
import com.ntg.CitizenLink.security.config.SecurityContextHelper;
import com.ntg.CitizenLink.service.interfaces.CaseNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/cases/{caseId}/notes")
@RequiredArgsConstructor
public class CaseNoteController {

    private final CaseNoteService caseNoteService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * US-15: Add a note to a case
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<NoteResponse> addNote(
            @PathVariable UUID caseId,
            @Valid @RequestBody AddNoteRequest request
    ) {
        log.info("REST request: POST /api/v1/cases/{}/notes", caseId);

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        NoteResponse response = caseNoteService.addNote(caseId, request, userId);

        log.info("REST response: POST /api/v1/cases/{}/notes - status: 201 CREATED", caseId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * US-15: Get all notes for a case (newest first)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<List<NoteResponse>> getNotesByCaseId(
            @PathVariable UUID caseId
    ) {
        log.info("REST request: GET /api/v1/cases/{}/notes", caseId);

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        List<NoteResponse> responses = caseNoteService.getNotesByCaseId(caseId, userId);

        log.info("REST response: GET /api/v1/cases/{}/notes - found {} notes", caseId, responses.size());
        return ResponseEntity.ok(responses);
    }

    /**
     * US-15: Get paginated notes for a case
     */
    @GetMapping("/paginated")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<Page<NoteResponse>> getNotesByCaseIdPaginated(
            @PathVariable UUID caseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.info("REST request: GET /api/v1/cases/{}/notes/paginated - page: {}, size: {}", caseId, page, size);

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NoteResponse> responses = caseNoteService.getNotesByCaseId(caseId, pageable, userId);

        log.info("REST response: GET /api/v1/cases/{}/notes/paginated - total: {}", caseId, responses.getTotalElements());
        return ResponseEntity.ok(responses);
    }

    /**
     * Get a single note by ID
     */
    @GetMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<NoteResponse> getNoteById(
            @PathVariable UUID caseId,
            @PathVariable UUID noteId
    ) {
        log.info("REST request: GET /api/v1/cases/{}/notes/{}", caseId, noteId);

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        NoteResponse response = caseNoteService.getNoteById(caseId, noteId, userId);

        log.info("REST response: GET /api/v1/cases/{}/notes/{} - note found", caseId, noteId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update a note
     */
    @PutMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<NoteResponse> updateNote(
            @PathVariable UUID caseId,
            @PathVariable UUID noteId,
            @Valid @RequestBody AddNoteRequest request
    ) {
        log.info("REST request: PUT /api/v1/cases/{}/notes/{}", caseId, noteId);

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        NoteResponse response = caseNoteService.updateNote(caseId, noteId, request, userId);

        log.info("REST response: PUT /api/v1/cases/{}/notes/{} - note updated", caseId, noteId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a note
     */
    @DeleteMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<Void> deleteNote(
            @PathVariable UUID caseId,
            @PathVariable UUID noteId
    ) {
        log.info("REST request: DELETE /api/v1/cases/{}/notes/{}", caseId, noteId);

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        caseNoteService.deleteNote(caseId, noteId, userId);

        log.info("REST response: DELETE /api/v1/cases/{}/notes/{} - status: 204 NO CONTENT", caseId, noteId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Count notes for a case
     */
    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<Long> countNotesByCaseId(
            @PathVariable UUID caseId
    ) {
        log.info("REST request: GET /api/v1/cases/{}/notes/count", caseId);

        UUID userId = securityContextHelper.getAuthenticatedUserId();
        long count = caseNoteService.countNotesByCaseId(caseId, userId);

        log.info("REST response: GET /api/v1/cases/{}/notes/count - {}", caseId, count);
        return ResponseEntity.ok(count);
    }
}
