package com.ntg.CitizenLink.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "citizen",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_citizen_national_id", columnNames = "national_id"),
        @UniqueConstraint(name = "uq_citizen_phone",       columnNames = "phone")
    },
    indexes = {
        @Index(name = "idx_citizen_full_name", columnList = "full_name")
    }
)
public class Citizen {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /**
     * Government-issued national identifier. Unique and required.
     */
    @Column(name = "national_id", nullable = false, length = 50)
    private String nationalId;

    /**
     * Nullable but unique — two citizens cannot share the same phone number.
     * PostgreSQL treats each NULL as distinct, so multiple NULLs are allowed.
     */
    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "preferred_language", length = 10)
    private String preferredLanguage = "en";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    /**
     * The staff member who registered this citizen record. Never null — always
     * derived from the authenticated user at creation time.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private AppUser createdByUser;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    protected Citizen() {}

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public AppUser getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(AppUser createdByUser) { this.createdByUser = createdByUser; }
}
