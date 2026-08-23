package com.ntg.citizenlink.dto.agent.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L-06: CreateCitizenRequest email validation must reject permissive/invalid
 * values (spaces, bare @, missing domain) and cap the length at 255, mirroring
 * the CreateUserRequest approach - while keeping the field optional.
 */
class CreateCitizenRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<String> violationsForEmail(String email) {
        CreateCitizenRequest request = new CreateCitizenRequest();
        request.setFullName("Valid Citizen");
        request.setNationalId("1234567890123456");
        request.setPhone("01234567890");
        request.setEmail(email);
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void validEmailIsAccepted() {
        assertThat(violationsForEmail("agent@example.com")).isEmpty();
    }

    @Test
    void nullEmailIsAccepted() {
        assertThat(violationsForEmail(null)).isEmpty();
    }

    @Test
    void emptyEmailIsAccepted() {
        assertThat(violationsForEmail("")).isEmpty();
    }

    @Test
    void emailWithSpaceAfterAtIsRejected() {
        assertThat(violationsForEmail("agent@ example.com")).contains("email");
    }

    @Test
    void emailWithSpaceInsideIsRejected() {
        assertThat(violationsForEmail("ag ent@example.com")).contains("email");
    }

    @Test
    void emailMissingDomainIsRejected() {
        assertThat(violationsForEmail("agent@")).contains("email");
    }

    @Test
    void emailWithBareAtIsRejected() {
        assertThat(violationsForEmail("@example.com")).contains("email");
    }

    @Test
    void emailWithDoubleAtIsRejected() {
        assertThat(violationsForEmail("agent@@example.com")).contains("email");
    }

    @Test
    void emailOverMaxLengthIsRejected() {
        String email = "u".repeat(60) + "@" + "d".repeat(63) + "." + "e".repeat(63)
                + "." + "f".repeat(63) + ".ghi";
        assertThat(email).hasSize(256);
        assertThat(violationsForEmail(email)).contains("email");
    }

    @Test
    void emailAtMaxLengthIsAccepted() {
        String email = "u".repeat(60) + "@" + "d".repeat(63) + "." + "e".repeat(63)
                + "." + "f".repeat(63) + ".gh";
        assertThat(email).hasSize(255);
        assertThat(violationsForEmail(email)).isEmpty();
    }
}