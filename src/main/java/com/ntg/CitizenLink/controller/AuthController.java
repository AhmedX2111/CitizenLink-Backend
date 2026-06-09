package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.AuthResponse;
import com.ntg.CitizenLink.dto.EncryptedAuthResponse;
import com.ntg.CitizenLink.dto.LoginRequest;
import com.ntg.CitizenLink.enums.ActionStatus;
import com.ntg.CitizenLink.enums.EventType;
import com.ntg.CitizenLink.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<EncryptedAuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.debug("Login request received for user: {}", request.username());
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails) {
        authService.logout(userDetails);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<EncryptedAuthResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Get current user request for: {}", userDetails.getUsername());
        return ResponseEntity.ok(authService.getCurrentUser(userDetails.getUsername()));
    }
}
