package com.ntg.CitizenLink.entities;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(
    name = "department",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_department_code", columnNames = "code")
    }
)
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name_en", nullable = false, length = 200)
    private String nameEn;

    @Column(name = "name_ar", nullable = false, length = 200)
    private String nameAr;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    protected Department() {}

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }

    public String getNameAr() { return nameAr; }
    public void setNameAr(String nameAr) { this.nameAr = nameAr; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
