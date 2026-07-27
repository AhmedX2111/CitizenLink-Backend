package com.ntg.CitizenLink.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.AuthenticationException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -------------------------------------------------------------------------
    // Validation errors (@Valid failures) → 400 BAD_REQUEST
    // -------------------------------------------------------------------------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> details = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.add(new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()));
        }
        log.warn("Validation failed for {}: {}",
                ex.getBindingResult().getTarget() != null
                        ? ex.getBindingResult().getTarget().getClass().getSimpleName()
                        : "unknown",
                details);
        ErrorResponse body = new ErrorResponse("VALIDATION_ERROR", "Validation failed", details);
        return ResponseEntity.badRequest().body(body);
    }

    // -------------------------------------------------------------------------
    // 401 Unauthorized - Authentication Errors
    // -------------------------------------------------------------------------
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: Bad credentials");
        ErrorResponse body = new ErrorResponse("BAD_CREDENTIALS", "Invalid username or password", null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledAccount(DisabledException ex) {
        log.warn("Authentication failed: Account disabled");
        ErrorResponse body = new ErrorResponse("ACCOUNT_DISABLED", "Account is disabled. Please contact support.", null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLockedAccount(LockedException ex) {
        log.warn("Authentication failed: Account locked");
        ErrorResponse body = new ErrorResponse("ACCOUNT_LOCKED", "Account is locked. Please contact support.", null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("AUTHENTICATION_FAILED", "Invalid username or password", null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    // -------------------------------------------------------------------------
    // 403 Forbidden - Authorization failure
    // -------------------------------------------------------------------------
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("FORBIDDEN", "Access denied", null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex) {
        log.warn("Security exception: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("FORBIDDEN", ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // -------------------------------------------------------------------------
    // 404 Not Found
    // -------------------------------------------------------------------------
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found");
        ErrorResponse body = new ErrorResponse("NOT_FOUND", ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // -------------------------------------------------------------------------
    // 409 Conflict - Workflow transition violation (WFL-01)
    // -------------------------------------------------------------------------
    @ExceptionHandler(IllegalTransitionException.class)
    public ResponseEntity<ErrorResponse> handleIllegalTransition(IllegalTransitionException ex) {
        log.warn("Illegal transition attempted: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse(ex.getCode(), ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // -------------------------------------------------------------------------
    // 409 Conflict - Database constraint / Duplicate resource
    // -------------------------------------------------------------------------
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("DATA_INTEGRITY_VIOLATION", "A record with this value already exists.", null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource detected");
        ErrorResponse body = new ErrorResponse("DUPLICATE_RESOURCE", ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request - General business rule violations
    // -------------------------------------------------------------------------
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("BAD_REQUEST", ex.getMessage(), null);
        return ResponseEntity.badRequest().body(body);
    }

    // -------------------------------------------------------------------------
    // 500 Internal Server Error - Generic fallback
    // -------------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception caught by GlobalExceptionHandler", ex);
        ErrorResponse body = new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", null);
        return ResponseEntity.internalServerError().body(body);
    }

    // -------------------------------------------------------------------------
    // Error envelope
    // -------------------------------------------------------------------------
    public record ErrorResponse(
            String code,
            String message,
            List<FieldErrorDetail> details
    ) {}

    public record FieldErrorDetail(
            String field,
            String message
    ) {}
}
