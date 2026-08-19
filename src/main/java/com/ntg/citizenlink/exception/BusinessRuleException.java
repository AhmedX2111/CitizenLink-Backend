package com.ntg.citizenlink.exception;

/**
 * Thrown when a request is rejected because it violates an intentional,
 * user-facing business rule (invalid date range, file-type/size constraint,
 * user-admin policy, active/inactive assignment rule, ...).
 *
 * The message is written by our own code specifically for the client, so it is
 * safe to surface in the 400 error envelope — unlike a raw
 * {@link IllegalArgumentException}, whose message may embed internal values
 * (stored file names, detected MIME types, runtime strings) that must never
 * reach the API consumer.
 *
 * Maps to HTTP 400 (see GlobalExceptionHandler).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}