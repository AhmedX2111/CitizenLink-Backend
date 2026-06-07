package com.ntg.CitizenLink.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "case_note",
    indexes = {
        @Index(name = "idx_case_note_case_id", columnList = "case_id")
    }
)
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CaseNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, updatable = false)
    private Case caseEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_id", nullable = false, updatable = false)
    private AppUser author;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    /**
     * true  = internal staff note, never exposed outside the portal.
     * false = public note (Phase 2 only — reserved for citizen-facing portal).
     * Defaults to true for safety.
     */
    @Column(name = "internal", nullable = false)
    private boolean internal = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;
}
