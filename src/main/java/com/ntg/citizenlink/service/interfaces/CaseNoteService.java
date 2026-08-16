package com.ntg.citizenlink.service.interfaces;

import com.ntg.citizenlink.dto.agent.request.AddNoteRequest;
import com.ntg.citizenlink.dto.agent.response.NoteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CaseNoteService {

    /**
     * US-15: Add a note to a case
     */
    NoteResponse addNote(UUID caseId, AddNoteRequest request, UUID authorId);

    /**
     * Get all notes for a case (newest first)
     */
    List<NoteResponse> getNotesByCaseId(UUID caseId, UUID requesterId);

    /**
     * Get paginated notes for a case
     */
    Page<NoteResponse> getNotesByCaseId(UUID caseId, Pageable pageable, UUID requesterId);

    /**
     * Get note by ID
     */
    NoteResponse getNoteById(UUID caseId, UUID noteId, UUID requesterId);

    /**
     * Update a note
     */
    NoteResponse updateNote(UUID caseId, UUID noteId, AddNoteRequest request, UUID userId);

    /**
     * Delete a note
     */
    void deleteNote(UUID caseId, UUID noteId, UUID userId);

    /**
     * Count notes for a case
     */
    long countNotesByCaseId(UUID caseId, UUID requesterId);
}
