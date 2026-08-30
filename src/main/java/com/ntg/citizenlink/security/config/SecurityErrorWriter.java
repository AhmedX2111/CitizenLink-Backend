package com.ntg.citizenlink.security.config;

import com.ntg.citizenlink.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * US-47: writes the standard {@code {code, message, details}} error envelope
 * for failures that happen inside the security filter chain — where
 * {@link GlobalExceptionHandler} (a Spring MVC {@code @ControllerAdvice})
 * never runs. Both the authentication entry point (anonymous → 401) and the
 * access-denied handler (authenticated but not permitted → 403) delegate here
 * so their response bodies match the MVC-layer schema exactly.
 *
 * The bodies deliberately expose no account identifiers, request paths, or
 * exception class names — only a fixed code and a fixed opaque message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    /** Anonymous / missing / invalid / expired credentials. */
    public void writeUnauthorized(HttpServletResponse response) {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }

    /** Authenticated, but the URL/role rule forbids the action. */
    public void writeForbidden(HttpServletResponse response) {
        write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    private void write(HttpServletResponse response, int status, String code, String message) {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            objectMapper.writeValue(response.getOutputStream(),
                    new GlobalExceptionHandler.ErrorResponse(code, message, null));
        } catch (IOException | JacksonException e) {
            // Best-effort: if the client has already disconnected there is
            // nothing meaningful left to write; log and let the status stand.
            // (IOException comes from response.getOutputStream(); Jackson 3
            // serialization failures are the unchecked JacksonException.)
            log.warn("Failed to write security error response: {}", e.getMessage());
        }
    }
}
