package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.GEH.DuplicateResourceException;
import com.ntg.CitizenLink.GEH.ResourceNotFoundException;
import com.ntg.CitizenLink.dto.agent.request.CreateUserRequest;
import com.ntg.CitizenLink.dto.agent.request.UpdateUserRequest;
import com.ntg.CitizenLink.dto.agent.request.UserSearchRequest;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.dto.agent.response.UserResponse;
import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.repositories.UserRepository;
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

        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());

        AppUser saved = userRepository.save(user);
        log.info("User updated successfully: id={}, role={}", saved.getId(), saved.getRole());

        return toResponse(saved);
    }

    // ── US-31: Deactivate/reactivate user ────────────────────────────────

    @Transactional
    public UserResponse deactivateUser(UUID id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", id));

        user.setActive(!user.getActive());
        user.setRefreshTokenJti(null);
        AppUser saved = userRepository.save(user);

        log.info("User {} status toggled: active={}, refresh tokens revoked", id, saved.getActive());
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
