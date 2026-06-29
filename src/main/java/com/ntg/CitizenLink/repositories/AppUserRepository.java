package com.ntg.CitizenLink.repositories;

import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<AppUser> findByRoleAndActiveTrue(UserRole role);
}
