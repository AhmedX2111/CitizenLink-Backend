package com.ntg.citizenlink.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M-15: DB constraint failures must never surface raw government identifiers
 * (national ID / phone) or schema details from logs or API responses.
 */
class GlobalExceptionHandlerPrivacyTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static final String POSTGRES_DUPLICATE =
            "could not execute statement [insert into citizen ...]; nested exception is "
            + "org.hibernate.exception.ConstraintViolationException: could not execute statement; "
            + "constraint [citizen_unique_national_id]; nested exception is org.postgresql.util.PSQLException: "
            + "ERROR: duplicate key value violates unique constraint \"citizen_unique_national_id\" "
            + "Detail: Key (national_id)=(4123456789012) already exists.";

    @Test
    void dataIntegrityViolation_responseMessage_hidesConflictingValueAndSchema() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(POSTGRES_DUPLICATE);

        var response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("DATA_INTEGRITY_VIOLATION");
        assertThat(response.getBody().message())
                .doesNotContain("4123456789012")
                .doesNotContain("national_id")
                .doesNotContain("citizen_unique");
    }

    @Test
    void extractConstraintName_parsesHibernateAndPostgresFormats() {
        assertThat(GlobalExceptionHandler.extractConstraintName(
                new IllegalStateException(POSTGRES_DUPLICATE)))
                .isEqualTo("citizen_unique_national_id");

        assertThat(GlobalExceptionHandler.extractConstraintName(
                new IllegalStateException("could not execute statement; constraint [uk_cases_number]; nested...")))
                .isEqualTo("uk_cases_number");

        assertThat(GlobalExceptionHandler.extractConstraintName(
                new IllegalStateException("Key (national_id)=(4123456789012) already exists.")))
                .isEqualTo("unknown");

        assertThat(GlobalExceptionHandler.extractConstraintName(null))
                .isEqualTo("unknown");
    }
}