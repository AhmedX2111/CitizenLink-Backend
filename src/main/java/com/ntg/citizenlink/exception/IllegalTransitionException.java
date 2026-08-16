package com.ntg.citizenlink.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested workflow transition is not valid for the
 * case's current status, or not permitted for the requester's role
 * (WFL-01: API must return 409 if the transition would be illegal).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class IllegalTransitionException extends RuntimeException {

    private final String code;

    public IllegalTransitionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
