package com.ntg.CitizenLink.entities;


import com.ntg.CitizenLink.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "app_user",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_app_user_username", columnNames = "username"),
        @UniqueConstraint(name = "uq_app_user_email",    columnNames = "email")
    },
    indexes = {
        @Index(name = "idx_app_user_role",   columnList = "role"),
        @Index(name = "idx_app_user_active", columnList = "active")
    }
)
@Setter
@Getter
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /**
     * BCrypt hash only — never store or return plain-text passwords.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    protected AppUser() {}

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------
//
//    public UUID getId() { return id; }
//
//    public String getUsername() { return username; }
//    public void setUsername(String username) { this.username = username; }
//
//    public String getPasswordHash() { return passwordHash; }
//    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
//
//    public String getDisplayName() { return displayName; }
//    public void setDisplayName(String displayName) { this.displayName = displayName; }
//
//    public String getEmail() { return email; }
//    public void setEmail(String email) { this.email = email; }
//
//    public UserRole getRole() { return role; }
//    public void setRole(UserRole role) { this.role = role; }
//
//    public boolean isActive() { return active; }
//    public void setActive(boolean active) { this.active = active; }
//
//    public OffsetDateTime getCreatedAt() { return createdAt; }
//    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
