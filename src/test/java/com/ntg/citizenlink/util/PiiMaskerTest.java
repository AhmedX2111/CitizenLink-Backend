package com.ntg.citizenlink.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    // ── maskNationalId ─────────────────────────────────────────────────

    @Test
    void maskNationalId_masksMiddleDigits() {
        assertThat(PiiMasker.maskNationalId("1234567890123456"))
                .isEqualTo("123****3456");
    }

    @Test
    void maskNationalId_shortValue_stillShowsLastFour() {
        assertThat(PiiMasker.maskNationalId("1234")).isEqualTo("1234");
    }

    @Test
    void maskNationalId_varyingLengths_producesConsistentMask() {
        // 15 digits: 3 prefix + **** + 4 suffix = "123****2345"
        assertThat(PiiMasker.maskNationalId("123456789012345")).isEqualTo("123****2345");
        assertThat(PiiMasker.maskNationalId("12345678901234567")).isEqualTo("123****4567");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void maskNationalId_nullOrBlank_returnsAsIs(String value) {
        assertThat(PiiMasker.maskNationalId(value)).isEqualTo(value);
    }

    @Test
    void maskNationalId_stripsWhitespaceBeforeMasking() {
        assertThat(PiiMasker.maskNationalId("1234 5678 9012 3456"))
                .isEqualTo("123****3456");
    }

    // ── maskPhone ────────────────────────────────────────────────────────

    @Test
    void maskPhone_masksMiddleDigits() {
        assertThat(PiiMasker.maskPhone("01012345678"))
                .isEqualTo("010****5678");
    }

    @Test
    void maskPhone_withSpaces_stripsAndMasks() {
        assertThat(PiiMasker.maskPhone("010 123 456 78"))
                .isEqualTo("010****5678");
    }

    @Test
    void maskPhone_shortValue_returnsUnchanged() {
        // 3 digits is too short to mask meaningfully
        assertThat(PiiMasker.maskPhone("010")).isEqualTo("010");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void maskPhone_nullOrBlank_returnsAsIs(String value) {
        assertThat(PiiMasker.maskPhone(value)).isEqualTo(value);
    }

    // ── maskEmail ────────────────────────────────────────────────────────

    @Test
    void maskEmail_masksLocalPartKeepingDomain() {
        assertThat(PiiMasker.maskEmail("ahmed@example.com"))
                .isEqualTo("a****@example.com");
    }

    @Test
    void maskEmail_singleCharLocalPart_masksRestOfLocal() {
        assertThat(PiiMasker.maskEmail("a@example.com"))
                .isEqualTo("a****@example.com");
    }

    @Test
    void maskEmail_noAtSymbol_returnsMasked() {
        assertThat(PiiMasker.maskEmail("notanemail"))
                .isEqualTo("****");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void maskEmail_nullOrBlank_returnsAsIs(String value) {
        assertThat(PiiMasker.maskEmail(value)).isEqualTo(value);
    }
}
