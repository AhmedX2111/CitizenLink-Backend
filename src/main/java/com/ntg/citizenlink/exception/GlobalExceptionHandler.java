package com.ntg.citizenlink.exception;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // -------------------------------------------------------------------------
    // Validation errors (@Valid failures) → 400 BAD_REQUEST
    // -------------------------------------------------------------------------
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
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
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request - Constraint violations on simple-type parameters
    // (e.g. @Min/@Max on @RequestParam). Spring 6.1+ method validation.
    // -------------------------------------------------------------------------
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> details = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String parameter = result.getMethodParameter().getParameterName();
            if (parameter == null) {
                parameter = "arg" + result.getMethodParameter().getParameterIndex();
            }
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                details.add(new FieldErrorDetail(parameter, error.getDefaultMessage()));
            }
        }
        log.warn("Parameter validation failed: {}", details);
        ErrorResponse body = new ErrorResponse("VALIDATION_ERROR", "Invalid parameter value(s)", details);
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request - Malformed request body (e.g. invalid JSON)
    // -------------------------------------------------------------------------
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Malformed request body: {}", safeMessage(ex));
        ErrorResponse body = new ErrorResponse("BAD_REQUEST", "Malformed request body", null);
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request - Missing required request parameter
    // -------------------------------------------------------------------------
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        log.warn("Missing required request parameter '{}'", ex.getParameterName());
        ErrorResponse body = new ErrorResponse(
                "BAD_REQUEST",
                "Required request parameter '" + ex.getParameterName() + "' is not present",
                null);
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    // -------------------------------------------------------------------------
    // 413 Payload Too Large - Upload exceeds the configured max size
    // -------------------------------------------------------------------------
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Upload rejected: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("PAYLOAD_TOO_LARGE", "Upload exceeds the maximum allowed size", null);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).headers(headers).body(body);
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
        // M-15: authentication exception messages can carry account identifiers;
        // log the exception class only.
        log.warn("Authentication failed: {}", ex.getClass().getSimpleName());
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
        // M-15: the raw message may embed the conflicting value — e.g.
        // PostgreSQL "Key (national_id)=(...)" — a government identifier plus a
        // schema disclosure. Log only the exception class and the constraint
        // name, never the message.
        Throwable root = ex.getMostSpecificCause();
        log.warn("Data integrity violation - causedBy: {}, constraint: {}",
                root != null ? root.getClass().getSimpleName() : ex.getClass().getSimpleName(),
                extractConstraintName(root));
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
    // 409 Conflict - Concurrent modification (optimistic-lock version conflict)
    // -------------------------------------------------------------------------
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent modification detected: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                "CONCURRENT_MODIFICATION",
                "This case was updated by another user. Refresh and retry.",
                null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request - Invalid path/query parameter type (e.g. malformed UUID)
    // -------------------------------------------------------------------------
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        // M-15: never echo the raw client-supplied value into the log
        // (log-injection / PII vector); log the parameter name only.
        log.warn("Invalid parameter type for '{}'", ex.getName());
        ErrorResponse body = new ErrorResponse("BAD_REQUEST", "Invalid value for parameter: " + ex.getName(), null);
        return ResponseEntity.badRequest().body(body);
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
    // 400 Bad Request - Malformed/corrupt encrypted ID (M-19). The ciphertext
    // is attacker-controlled, so it is never logged and never echoed back.
    // -------------------------------------------------------------------------
    @ExceptionHandler(InvalidEncryptedIdException.class)
    public ResponseEntity<ErrorResponse> handleInvalidEncryptedId(InvalidEncryptedIdException ex) {
        log.warn("Invalid encrypted ID rejected");
        ErrorResponse body = new ErrorResponse("INVALID_ENCRYPTED_ID", ex.getMessage(), null);
        return ResponseEntity.badRequest().body(body);
    }

    // -------------------------------------------------------------------------
    // Every other Spring MVC exception (method not allowed, unsupported media
    // type, not acceptable, ...) is handled by the inherited
    // ResponseEntityExceptionHandler#handleException dispatcher and funnels
    // into handleExceptionInternal below, which wraps them in our error
    // envelope and logs client errors at WARN without a stack trace.
    // -------------------------------------------------------------------------
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        int code = statusCode.value();
        if (code >= 500) {
            log.error("Spring MVC handler mapped {} to {}: {}",
                    ex.getClass().getSimpleName(), code, ex.getMessage(), ex);
        } else {
            log.warn("Spring MVC {} error ({}) for {}: {}",
                    code, ex.getClass().getSimpleName(), request.getDescription(false), safeMessage(ex));
        }
        ErrorResponse payload = new ErrorResponse(errorCodeFor(code), messageFor(code), null);
        return ResponseEntity.status(statusCode).headers(headers).body(payload);
    }

    // -------------------------------------------------------------------------
    // 500 Internal Server Error - Generic fallback. Only reached for exceptions
    // that neither our handlers nor the inherited Spring MVC handlers cover,
    // i.e. genuine server errors.
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

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static final Pattern CONSTRAINT_NAME_PATTERN =
            Pattern.compile("constraint\\s+[\\[\"]?([\\w.]+)[\\]\"]?");

    /**
     * M-15: extract only the DB constraint name from an exception cause chain,
     * discarding the conflicting value PostgreSQL/Hibernate may embed (e.g.
     * {@code Key (national_id)=(...)}). Returns "unknown" when no constraint
     * name can be found.
     */
    static String extractConstraintName(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null) {
                Matcher matcher = CONSTRAINT_NAME_PATTERN.matcher(message);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return "unknown";
    }

    private String errorCodeFor(int code) {
        return switch (code) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> code >= 500 ? "INTERNAL_ERROR" : "HTTP_" + code;
        };
    }

    private String messageFor(int code) {
        return switch (code) {
            case 400 -> "Invalid request";
            case 401 -> "Unauthorized";
            case 403 -> "Access denied";
            case 404 -> "Resource not found";
            case 405 -> "Method not allowed";
            case 409 -> "Request conflicts with the current state of the resource";
            case 413 -> "Request entity is too large";
            case 415 -> "Unsupported media type";
            default -> "An unexpected error occurred";
        };
    }
}