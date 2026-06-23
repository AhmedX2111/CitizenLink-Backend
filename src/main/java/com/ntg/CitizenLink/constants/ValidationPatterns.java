package com.ntg.CitizenLink.constants;

public final class ValidationPatterns {

    /**
     * National ID format — exactly 10 digits.
     * Single source of truth: used by both citizen creation and case creation
     * to guarantee both endpoints agree on what a valid National ID looks like.
     */
    public static final String NATIONAL_ID_PATTERN = "^[0-9]{16}$";

    public static final String NATIONAL_ID_MESSAGE =
            "National ID must be exactly 10 digits";

    private ValidationPatterns() {
        // utility class — prevent instantiation
    }
}
