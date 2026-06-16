package com.ntg.CitizenLink.GEH;

public class DuplicateResourceException extends RuntimeException{

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resource, String field, String value) {
        super(String.format("%s with %s '%s' already exists", resource, field, value));
    }
}
