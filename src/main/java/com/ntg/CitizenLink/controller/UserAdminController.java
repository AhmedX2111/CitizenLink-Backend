package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.request.UserSearchRequest;
import com.ntg.CitizenLink.dto.agent.response.PagedResponse;
import com.ntg.CitizenLink.dto.agent.response.UserResponse;
import com.ntg.CitizenLink.enums.UserRole;
import com.ntg.CitizenLink.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * US-29: User administration — ADMIN only.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    /**
     * GET /api/v1/users?role=AGENT&active=true&page=0&size=20
     *
     * All parameters optional. Returns paginated list of all users.
     * ADMIN only — other roles receive 403.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<UserResponse>> getUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        UserSearchRequest request = new UserSearchRequest();
        request.setRole(role);
        request.setActive(active);
        request.setPage(page);
        request.setSize(size);

        return ResponseEntity.ok(userAdminService.getUsers(request));
    }
}