package com.ntg.citizenlink.service;

import com.ntg.citizenlink.exception.DuplicateResourceException;
import com.ntg.citizenlink.exception.ResourceNotFoundException;
import com.ntg.citizenlink.dto.agent.request.CreateUserRequest;
import com.ntg.citizenlink.dto.agent.request.UpdateUserRequest;
import com.ntg.citizenlink.dto.agent.request.UserSearchRequest;
import com.ntg.citizenlink.dto.agent.response.PagedResponse;
import com.ntg.citizenlink.dto.agent.response.UserResponse;
import com.ntg.citizenlink.entities.AppUser;
import com.ntg.citizenlink.enums.UserRole;
import com.ntg.citizenlink.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ── US-29: List users ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getUsers(UserSearchRequest request) {
        log.info("Admin user list: role={}, active={}, page={}",
                request.getRole(), request.getActive(), request.getPage());

        PageRequest pageable = PageRequest.of(request.getPage(), request.getSize());

        Page<AppUser> page = userRepository.findAllFiltered(
                request.getRole(), request.getActive(), pageable);

        List<UserResponse> content = page.getContent()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    // ── US-30: Get single user ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", id));
        return toResponse(user);
    }

    // ── US-30: Create user ───────────────────────────────────────────────

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Admin creating user: username={}, role={}", request.getUsername(), request.getRole());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("User", "username", request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);

        AppUser saved = userRepository.save(user);
        log.info("User created successfully: id={}, username={}", saved.getId(), saved.getUsername());

        return toResponse(saved);
    }

    // ── US-31: Update user details/role ──────────────────────────────────

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        log.info("Admin updating user: id={}", id);

        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", id));

        if (!user.getEmail().equals(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        // M-06: never allow the last remaining active ADMIN to be demoted to a
        // non-ADMIN role — same permanent-lockout rationale as deactivation.
        if (Boolean.TRUE.equals(user.getActive())
                && user.getRole() == UserRole.ADMIN
                && request.getRole() != UserRole.ADMIN
                && userRepository.countByRoleAndActive(UserRole.ADMIN, true) <= 1) {
            throw new IllegalArgumentException("Cannot demote the last active ADMIN account");
        }

        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());

        AppUser saved = userRepository.save(user);
        log.info("User updated successfully: id={}, role={}", saved.getId(), saved.getRole());

        return toResponse(saved);
    }

    // ── US-31: Deactivate/reactivate user ────────────────────────────────

    @Transactional
    public UserResponse deactivateUser(UUID id, UUID callerId) {
        log.info("Admin {} deactivating user: id={}", callerId, id);

        if (id.equals(callerId)) {
            throw new IllegalArgumentException("You cannot deactivate your own account");
        }

        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", id));

        // M-06: never allow the last remaining active ADMIN to be deactivated —
        // otherwise /api/v1/users/** (ADMIN-only, no bootstrap path) would be
        // permanently locked out.
        if (Boolean.TRUE.equals(user.getActive())
                && user.getRole() == UserRole.ADMIN
                && userRepository.countByRoleAndActive(UserRole.ADMIN, true) <= 1) {
            throw new IllegalArgumentException("Cannot deactivate the last active ADMIN account");
        }

        user.setActive(false);
        user.setRefreshTokenJti(null);
        AppUser saved = userRepository.save(user);

        log.info("User {} deactivated (active=false), refresh tokens revoked", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public UserResponse activateUser(UUID id, UUID callerId) {
        log.info("Admin {} reactivating user: id={}", callerId, id);

        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", id));

        user.setActive(true);
        AppUser saved = userRepository.save(user);

        log.info("User {} reactivated (active=true)", saved.getId());
        return toResponse(saved);
    }

    // ── Shared mapper ────────────────────────────────────────────────────

    private UserResponse toResponse(AppUser u) {
        String hex  = u.getId().toString().replace("-", "").substring(0, 4).toUpperCase();
        String role = u.getRole().name().substring(0, 1);
        String ref  = "USR-" + hex + "-" + role;

        return UserResponse.builder()
                .id(u.getId())
                .displayRef(ref)
                .username(u.getUsername())
                .displayName(u.getDisplayName())
                .email(u.getEmail())
                .role(u.getRole())
                .active(u.getActive())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
