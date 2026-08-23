package com.ntg.citizenlink.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when {@code IdEncryptionService} is asked to decrypt a value that is
 * not a valid, well-formed encrypted ID — malformed Base64, a corrupted GCM
 * tag, or ciphertext that does not decrypt to a UUID.
 *
 * The input is always client-supplied (it arrives straight from a request
 * path), so it is never included in the message or the logs.
 *
 * Maps to HTTP 400 (see GlobalExceptionHandler).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidEncryptedIdException extends RuntimeException {

    public InvalidEncryptedIdException(String message) {
        super(message);
    }
}