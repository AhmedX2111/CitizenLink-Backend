package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.service.interfaces.IdEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Confirms the ID obfuscation key is a real 256-bit key: the constructor
 * requires a Base64-encoded value that decodes to exactly 32 bytes and fails
 * fast otherwise, and encrypt/decrypt round-trips with a fresh, random GCM IV.
 */
class IdEncryptionServiceImplTest {

    private static final String VALID_KEY = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";
    private static final String SHORT_KEY = "MTIzNDU2Nzg5MGE=";
    private static final String LONG_KEY = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIz";

    private IdEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new IdEncryptionServiceImpl(VALID_KEY);
    }

    @Nested
    class ConstructorValidation {

        @Test
        void rejectsNonBase64Key() {
            assertThatThrownBy(() -> new IdEncryptionServiceImpl("not base64###"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void rejectsKeyShorterThan32Bytes() {
            assertThatThrownBy(() -> new IdEncryptionServiceImpl(SHORT_KEY))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void rejectsKeyLongerThan32Bytes() {
            assertThatThrownBy(() -> new IdEncryptionServiceImpl(LONG_KEY))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void rejectsEmptyKey() {
            assertThatThrownBy(() -> new IdEncryptionServiceImpl(""))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class EncryptAndDecrypt {

        @Test
        void encryptDecrypt_roundTripsTheId() {
            UUID id = UUID.randomUUID();

            String encrypted = service.encryptId(id);

            assertThat(encrypted).isNotBlank();
            assertThat(service.decryptId(encrypted)).isEqualTo(id);
        }

        @Test
        void encryptId_producesUniqueTokensForSameId() {
            UUID id = UUID.randomUUID();
            HashSet<String> tokens = new HashSet<>();

            for (int i = 0; i < 5; i++) {
                tokens.add(service.encryptId(id));
            }

            assertThat(tokens).hasSize(5);
        }

        @Test
        void decryptId_throwsOnGarbage() {
            assertThatThrownBy(() -> service.decryptId("not-valid-ciphertext"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        void decryptIdSafely_returnsNullOnGarbage() {
            assertThat(service.decryptIdSafely("not-valid-ciphertext")).isNull();
        }

        @Test
        void isValidEncryptedId_distinguishesValidFromGarbage() {
            String encrypted = service.encryptId(UUID.randomUUID());

            assertThat(service.isValidEncryptedId(encrypted)).isTrue();
            assertThat(service.isValidEncryptedId("garbage")).isFalse();
            assertThat(service.isValidEncryptedId(null)).isFalse();
        }
    }
}