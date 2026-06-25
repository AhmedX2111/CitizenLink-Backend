package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.GEH.ResourceNotFoundException;
import com.ntg.CitizenLink.dto.agent.request.AddNoteRequest;
import com.ntg.CitizenLink.dto.agent.response.NoteResponse;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.entities.CaseNote;
import com.ntg.CitizenLink.repositories.AppUserRepository;
import com.ntg.CitizenLink.repositories.CaseNoteRepository;
import com.ntg.CitizenLink.repositories.CaseRepository;
import com.ntg.CitizenLink.service.interfaces.CaseNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseNoteServiceImpl implements CaseNoteService {

    private final CaseNoteRepository caseNoteRepository;
    private final CaseRepository caseRepository;
    private final AppUserRepository appUserRepository;

    @Override
    @Transactional
    public NoteResponse addNote(UUID caseId, AddNoteRequest request, UUID authorId) {
        log.info("Adding note to case: {} by user: {}", caseId, authorId);

        // Validate case exists
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Case", caseId));

        // Validate author exists
        AppUser author = appUserRepository.findById(authorId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", authorId));

        // Create note using existing CaseNote entity
        CaseNote note = new CaseNote();
        note.setCaseEntity(caseEntity);
        note.setAuthor(author);
        note.setBody(request.getBody());
        note.setInternal(request.getInternal() != null ? request.getInternal() : true);

        CaseNote savedNote = caseNoteRepository.save(note);

        log.info("Note added successfully with ID: {}", savedNote.getId());

        return toResponse(savedNote);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoteResponse> getNotesByCaseId(UUID caseId) {
        log.debug("Fetching notes for case: {}", caseId);

        // Verify case exists
        if (!caseRepository.existsById(caseId)) {
            throw ResourceNotFoundException.of("Case", caseId);
        }

        List<CaseNote> notes = caseNoteRepository.findByCaseIdOrderByCreatedAtDesc(caseId);

        return notes.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NoteResponse> getNotesByCaseId(UUID caseId, Pageable pageable) {
        log.debug("Fetching paginated notes for case: {}", caseId);

        if (!caseRepository.existsById(caseId)) {
            throw ResourceNotFoundException.of("Case", caseId);
        }

        Page<CaseNote> notes = caseNoteRepository.findByCaseIdOrderByCreatedAtDesc(caseId, pageable);

        return notes.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public NoteResponse getNoteById(UUID noteId) {
        log.debug("Fetching note by ID: {}", noteId);

        CaseNote note = caseNoteRepository.findById(noteId)
                .orElseThrow(() -> ResourceNotFoundException.of("CaseNote", noteId));

        return toResponse(note);
    }

    @Override
    @Transactional
    public NoteResponse updateNote(UUID noteId, AddNoteRequest request, UUID userId) {
        log.info("Updating note: {} by user: {}", noteId, userId);

        CaseNote note = caseNoteRepository.findById(noteId)
                .orElseThrow(() -> ResourceNotFoundException.of("CaseNote", noteId));

        // Check if user is the author (or admin)
        if (!note.getAuthor().getId().equals(userId)) {
            // Check if user is admin (optional - you can add admin override)
            AppUser user = appUserRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of("AppUser", userId));

            // Allow only if user is the author or ADMIN
            if (!note.getAuthor().getId().equals(userId) &&
                    !"ADMIN".equals(user.getRole().name())) {
                throw new SecurityException("You are not authorized to update this note");
            }
        }

        note.setBody(request.getBody());
        if (request.getInternal() != null) {
            note.setInternal(request.getInternal());
        }

        CaseNote updatedNote = caseNoteRepository.save(note);

        log.info("Note updated successfully: {}", noteId);

        return toResponse(updatedNote);
    }

    @Override
    @Transactional
    public void deleteNote(UUID noteId, UUID userId) {
        log.info("Deleting note: {} by user: {}", noteId, userId);

        CaseNote note = caseNoteRepository.findById(noteId)
                .orElseThrow(() -> ResourceNotFoundException.of("CaseNote", noteId));

        // Check if user is the author (or admin)
        if (!note.getAuthor().getId().equals(userId)) {
            AppUser user = appUserRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of("AppUser", userId));

            if (!"ADMIN".equals(user.getRole().name())) {
                throw new SecurityException("You are not authorized to delete this note");
            }
        }

        caseNoteRepository.deleteById(noteId);

        log.info("Note deleted successfully: {}", noteId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countNotesByCaseId(UUID caseId) {
        return caseNoteRepository.countByCaseEntityId(caseId);
    }

    /**
     * Convert CaseNote entity to NoteResponse DTO
     */
    private NoteResponse toResponse(CaseNote note) {
        return NoteResponse.builder()
                .id(note.getId())
                .caseId(note.getCaseEntity().getId())
                .authorId(note.getAuthor().getId())
                .authorName(note.getAuthor().getDisplayName())
                .authorRole(note.getAuthor().getRole().name())
                .body(note.getBody())
                .internal(note.getInternal())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
