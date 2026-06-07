package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.AuthResponse;
import com.ntg.CitizenLink.dto.LoginRequest;
import com.ntg.CitizenLink.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints (BRD §5.1).
 *
 *   POST /api/v1/auth/login   — public, returns JWT + profile (AUTH-01, AUTH-02)
 *   GET  /api/v1/auth/me      — protected, returns current user profile (AUTH-02)
 *
 * The controller is intentionally thin — all logic lives in AuthService.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Authenticates username + password and returns a signed JWT.
     *
     * 200 OK      → { token, id, username, displayName, email, role }
     * 400         → validation errors (blank username/password)
     * 401         → bad credentials or inactive account
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Returns the currently authenticated user's profile.
     * Requires a valid Bearer token in the Authorization header.
     *
     * @AuthenticationPrincipal injects the UserDetails that the filter placed
     * in the SecurityContext — no need to parse the token again.
     *
     * 200 OK      → { token: null, id, username, displayName, email, role }
     * 401         → missing or expired token
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(authService.getCurrentUser(userDetails.getUsername()));
    }
}
