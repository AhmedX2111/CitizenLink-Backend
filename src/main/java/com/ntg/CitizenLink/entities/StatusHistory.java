package com.ntg.CitizenLink.entities;


import com.ntg.CitizenLink.CaseStatus;
import com.ntg.CitizenLink.WorkflowAction;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only audit log of every workflow transition on a case.
 *
 * Rules:
 *   - Records are never updated or deleted.
 *   - fromStatus is NULL only for the initial CREATE event (no prior status exists).
 *   - comment stores the mandatory suspend reason when action = SUSPEND.
 *   - One record is written per transition, always inside the same transaction
 *     as the Case status update.
 */
@Entity
@Table(
    name = "status_history",
    indexes = {
        @Index(name = "idx_status_history_case_id",    columnList = "case_id"),
        @Index(name = "idx_status_history_created_at", columnList = "created_at")
    }
)
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, updatable = false)
    private Case caseEntity;

    /**
     * NULL on the initial CREATE event — there is no prior status at that point.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private CaseStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private CaseStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private WorkflowAction action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_user_id", nullable = false, updatable = false)
    private AppUser changedByUser;

    /**
     * Required when action = SUSPEND (stores the mandatory reason).
     * Optional free-text comment on all other actions.
     */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    protected StatusHistory() {}

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }

    public Case getCaseEntity() { return caseEntity; }
    public void setCaseEntity(Case caseEntity) { this.caseEntity = caseEntity; }

    public CaseStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(CaseStatus fromStatus) { this.fromStatus = fromStatus; }

    public CaseStatus getToStatus() { return toStatus; }
    public void setToStatus(CaseStatus toStatus) { this.toStatus = toStatus; }

    public WorkflowAction getAction() { return action; }
    public void setAction(WorkflowAction action) { this.action = action; }

    public AppUser getChangedByUser() { return changedByUser; }
    public void setChangedByUser(AppUser changedByUser) { this.changedByUser = changedByUser; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
