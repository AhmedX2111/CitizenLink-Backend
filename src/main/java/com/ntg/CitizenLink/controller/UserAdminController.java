package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.request.CreateUserRequest;
import com.ntg.CitizenLink.dto.agent.request.UpdateUserRequest;
import com.ntg.CitizenLink.dto.agent.request.UserSearchRequest;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.dto.agent.response.UserResponse;
import com.ntg.CitizenLink.enums.UserRole;
import com.ntg.CitizenLink.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * US-29, US-30, US-31: User administration — ADMIN only.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    // ── US-29: List users ────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<UserResponse>> getUsers(
            @Valid UserSearchRequest request) {

        return ResponseEntity.ok(userAdminService.getUsers(request));
    }

    // ── US-30: Get single user ───────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userAdminService.getUserById(id));
    }

    // ── US-30: Create user ───────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("REST request: POST /api/v1/users - create user: {}", request.getUsername());
        UserResponse response = userAdminService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── US-31: Update user details/role ──────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {

        log.info("REST request: PUT /api/v1/users/{} - update user", id);
        UserResponse response = userAdminService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }

    // ── US-31: Deactivate/reactivate user ────────────────────────────────

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable UUID id) {
        log.info("REST request: PUT /api/v1/users/{}/deactivate", id);
        UserResponse response = userAdminService.deactivateUser(id);
        return ResponseEntity.ok(response);
    }
}
