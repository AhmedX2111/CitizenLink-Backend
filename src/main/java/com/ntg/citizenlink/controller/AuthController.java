package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.dto.EncryptedAuthResponse;
import com.ntg.citizenlink.dto.LoginRequest;
import com.ntg.citizenlink.exception.GlobalExceptionHandler;
import com.ntg.citizenlink.security.JwtBlocklist;
import com.ntg.citizenlink.security.config.JwtProperties;
import com.ntg.citizenlink.service.interfaces.AuthService;
import com.ntg.citizenlink.service.interfaces.JwtService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints (BRD §5.1).
 *   POST /api/v1/auth/login    — public, returns access token in body, refresh token in HttpOnly cookie (AUTH-01, AUTH-02)
 *   POST /api/v1/auth/refresh  — public, reads refresh token from cookie, rotates it, returns new access token (AUTH-03)
 *   POST /api/v1/auth/logout   — protected, clears refresh cookie and revokes token (AUTH-04)
 *   GET  /api/v1/auth/me       — protected, returns current user profile (AUTH-02)
 * Security:
 *   - Access token is short-lived (e.g. 15min) and returned only in the response body.
 *   - Refresh token is stored in an HttpOnly, Secure, SameSite=Strict cookie — invisible to JavaScript,
 *     immune to XSS, and only sent automatically on same-site requests to /auth/refresh.
 *   - The refresh token is never returned in the response body.
 * The controller is intentionally thin — all logic lives in AuthService.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final JwtBlocklist jwtBlocklist;
    private final JwtProperties jwtProperties;

    private final Map<String, AtomicBoolean> refreshInProgress = new ConcurrentHashMap<>();

    @PostMapping("/login")
    @SecurityRequirements
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
                .secure(jwtProperties.refreshCookieSecure() || servletRequest.isSecure())
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
    @SecurityRequirements
    public ResponseEntity<?> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        log.info("REST request: POST /api/v1/auth/refresh");

        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("REST response: POST /api/v1/auth/refresh - status: 401 - missing refresh token cookie");
            return unauthorizedWithClearedCookie(servletRequest);
        }

        String username;
        try {
            username = jwtService.extractUsername(refreshToken);
        } catch (Exception e) {
            log.warn("REST response: POST /api/v1/auth/refresh - status: 401 - invalid token format");
            return unauthorizedWithClearedCookie(servletRequest);
        }

        if (username == null || username.isBlank()) {
            log.warn("REST response: POST /api/v1/auth/refresh - status: 401 - invalid token format");
            return unauthorizedWithClearedCookie(servletRequest);
        }

        // Prevent concurrent refresh requests for the same user
        AtomicBoolean inProgress = refreshInProgress.computeIfAbsent(username, k -> new AtomicBoolean(false));
        if (!inProgress.compareAndSet(false, true)) {
            log.warn("REST response: POST /api/v1/auth/refresh - status: 401 - refresh already in progress for user: {}", username);
            return unauthorizedWithClearedCookie(servletRequest);
        }

        try {
            long startTime = System.currentTimeMillis();
            EncryptedAuthResponse authResponse = authService.refreshToken(refreshToken);
            long duration = System.currentTimeMillis() - startTime;

            log.info("REST response: POST /api/v1/auth/refresh - status: 200 OK, duration: {}ms", duration);

            ResponseCookie newRefreshCookie = ResponseCookie.from("refresh_token", authResponse.refreshToken())
                    .httpOnly(true)
                    .secure(jwtProperties.refreshCookieSecure() || servletRequest.isSecure())
                    .sameSite("Strict")
                    .path("/api/v1/auth")
                    .maxAge(jwtProperties.refreshExpirationMs() / 1000)
                    .build();

            EncryptedAuthResponse body = new EncryptedAuthResponse(
                    authResponse.token(), null, authResponse.encryptedId(),
                    authResponse.username(), authResponse.displayName(),
                    authResponse.email(), authResponse.role());

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, newRefreshCookie.toString())
                    .body(body);
        } catch (AuthenticationException e) {
            // US-45: re-throw so the @ControllerAdvice (GlobalExceptionHandler)
            // produces the standard 401 error envelope (ACCOUNT_DISABLED for
            // inactive users, BAD_CREDENTIALS otherwise) instead of an empty body.
            log.warn("REST response: POST /api/v1/auth/refresh - status: 401 - {}", e.getMessage());
            response.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie(servletRequest).toString());
            throw e;
        } finally {
            // Release the lock
            inProgress.set(false);
            refreshInProgress.remove(username, inProgress);
        }
    }

    private ResponseCookie clearRefreshCookie(HttpServletRequest servletRequest) {
        return ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(jwtProperties.refreshCookieSecure() || servletRequest.isSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    /**
     * US-47: the refresh early-401 paths (missing/malformed/blank token and
     * concurrent refresh) must return the standard {@code {code, message,
     * details}} envelope — previously an empty body — while preserving the
     * US-44 cookie-clearing behaviour (the refresh cookie is always cleared on
     * a 401 so a stale cookie cannot be replayed).
     */
    private ResponseEntity<GlobalExceptionHandler.ErrorResponse> unauthorizedWithClearedCookie(
            HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie(servletRequest).toString())
                .body(new GlobalExceptionHandler.ErrorResponse(
                        "UNAUTHORIZED", "Authentication required", null));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest servletRequest) {
        String username = userDetails != null ? userDetails.getUsername() : "unknown";
        log.info("REST request: POST /api/v1/auth/logout - username: {}", username);

        String authHeader = servletRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            String jti = jwtService.extractJti(jwt);
            Date expiry = jwtService.extractExpiration(jwt);
            if (jti != null) {
                jwtBlocklist.block(jti, expiry);
            }
        }

        authService.logout(userDetails);

        log.info("REST response: POST /api/v1/auth/logout - status: 200 OK");

        ResponseCookie clearCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(jwtProperties.refreshCookieSecure() || servletRequest.isSecure())
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
