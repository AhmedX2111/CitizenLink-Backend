package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.EncryptedAuthResponse;
import com.ntg.CitizenLink.dto.LoginRequest;
import com.ntg.CitizenLink.security.config.JwtProperties;
import com.ntg.CitizenLink.service.interfaces.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints (BRD §5.1).
 *
 *   POST /api/v1/auth/login    — public, returns access token in body, refresh token in HttpOnly cookie (AUTH-01, AUTH-02)
 *   POST /api/v1/auth/refresh  — public, reads refresh token from cookie, rotates it, returns new access token (AUTH-03)
 *   POST /api/v1/auth/logout   — protected, clears refresh cookie and revokes token (AUTH-04)
 *   GET  /api/v1/auth/me       — protected, returns current user profile (AUTH-02)
 *
 * Security:
 *   - Access token is short-lived (e.g. 15min) and returned only in the response body.
 *   - Refresh token is stored in an HttpOnly, Secure, SameSite=Strict cookie — invisible to JavaScript,
 *     immune to XSS, and only sent automatically on same-site requests to /auth/refresh.
 *   - The refresh token is never returned in the response body.
 *
 * The controller is intentionally thin — all logic lives in AuthService.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @PostMapping("/login")
    public ResponseEntity<EncryptedAuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        log.info("REST request: POST /api/v1/auth/login - username: {}", request.username());

        long startTime = System.currentTimeMillis();
        EncryptedAuthResponse response = authService.login(request);
        long duration = System.currentTimeMillis() - startTime;

        log.info("REST response: POST /api/v1/auth/login - status: 200 OK, duration: {}ms", duration);

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", response.refreshToken())
                .httpOnly(true)
                .secure(servletRequest.isSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(jwtProperties.refreshExpirationMs() / 1000)
                .build();

        EncryptedAuthResponse body = new EncryptedAuthResponse(
                response.token(), null, response.encryptedId(),
                response.username(), response.displayName(),
                response.email(), response.role());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<EncryptedAuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest servletRequest) {
        log.info("REST request: POST /api/v1/auth/refresh");

        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("REST response: POST /api/v1/auth/refresh - status: 400 - missing refresh token cookie");
            return ResponseEntity.badRequest().build();
        }

        long startTime = System.currentTimeMillis();
        EncryptedAuthResponse response = authService.refreshToken(refreshToken);
        long duration = System.currentTimeMillis() - startTime;

        log.info("REST response: POST /api/v1/auth/refresh - status: 200 OK, duration: {}ms", duration);

        ResponseCookie newRefreshCookie = ResponseCookie.from("refresh_token", response.refreshToken())
                .httpOnly(true)
                .secure(servletRequest.isSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(jwtProperties.refreshExpirationMs() / 1000)
                .build();

        EncryptedAuthResponse body = new EncryptedAuthResponse(
                response.token(), null, response.encryptedId(),
                response.username(), response.displayName(),
                response.email(), response.role());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newRefreshCookie.toString())
                .body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest servletRequest) {
        String username = userDetails != null ? userDetails.getUsername() : "unknown";
        log.info("REST request: POST /api/v1/auth/logout - username: {}", username);

        authService.logout(userDetails);

        log.info("REST response: POST /api/v1/auth/logout - status: 200 OK");

        ResponseCookie clearCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(servletRequest.isSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
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
