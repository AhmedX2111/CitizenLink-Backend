package com.ntg.CitizenLink.repositories;

import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Dedicated repository for user-admin queries (US-29).
 * The existing AppUserRepository is used by security/auth code —
 * keeping admin queries here avoids polluting that class.
 */
@Repository
public interface UserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * US-29: paginated list with optional role and active filters.
     * Null parameters are treated as "no filter" via JPQL coalesce.
     */
    @Query("""
        SELECT u FROM AppUser u
        WHERE (:role IS NULL OR u.role = :role)
          AND (:active IS NULL OR u.active = :active)
        ORDER BY u.displayName ASC
        """)
    Page<AppUser> findAllFiltered(
            @Param("role")   UserRole role,
            @Param("active") Boolean active,
            Pageable pageable);
}