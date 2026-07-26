package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.EncryptedAuthResponse;
import com.ntg.CitizenLink.dto.LoginRequest;
import com.ntg.CitizenLink.dto.RefreshTokenRequest;
import com.ntg.CitizenLink.service.interfaces.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints (BRD §5.1).
 *
 *   POST /api/v1/auth/login    — public, returns access+refresh tokens + profile (AUTH-01, AUTH-02)
 *   POST /api/v1/auth/refresh  — public, rotates tokens (requires valid refresh token)
 *   POST /api/v1/auth/logout   — protected, clears refresh token
 *   GET  /api/v1/auth/me       — protected, returns current user profile (AUTH-02)
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
        log.info("REST request: POST /api/v1/auth/login - username: {}", request.username());

        long startTime = System.currentTimeMillis();
        EncryptedAuthResponse response = authService.login(request);
        long duration = System.currentTimeMillis() - startTime;

        log.info("REST response: POST /api/v1/auth/login - status: 200 OK, duration: {}ms", duration);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<EncryptedAuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("REST request: POST /api/v1/auth/refresh");

        long startTime = System.currentTimeMillis();
        EncryptedAuthResponse response = authService.refreshToken(request.refreshToken());
        long duration = System.currentTimeMillis() - startTime;

        log.info("REST response: POST /api/v1/auth/refresh - status: 200 OK, duration: {}ms", duration);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "unknown";
        log.info("REST request: POST /api/v1/auth/logout - username: {}", username);

        authService.logout(userDetails);

        log.info("REST response: POST /api/v1/auth/logout - status: 200 OK");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<EncryptedAuthResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            log.warn("REST request: GET /api/v1/auth/me - no authentication");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("REST request: GET /api/v1/auth/me - username: {}", userDetails.getUsername());

        long startTime = System.currentTimeMillis();
        EncryptedAuthResponse response = authService.getCurrentUser(userDetails.getUsername());
        long duration = System.currentTimeMillis() - startTime;

        log.info("REST response: GET /api/v1/auth/me - status: 200 OK, duration: {}ms", duration);
        return ResponseEntity.ok(response);
    }
}
