package com.ntg.citizenlink.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchNormalizerTest {

    // ── normalizeArabic ──────────────────────────────────────────────────

    @Test
    void normalizeArabic_removesTashkeel() {
        assertThat(SearchNormalizer.normalizeArabic("مُحَمَّد"))
                .isEqualTo("محمد");
    }

    @Test
    void normalizeArabic_normalizesAlefVariants() {
        assertThat(SearchNormalizer.normalizeArabic("أحمد"))
                .isEqualTo("احمد");
        assertThat(SearchNormalizer.normalizeArabic("إبراهيم"))
                .isEqualTo("ابراهيم");
        assertThat(SearchNormalizer.normalizeArabic("آل"))
                .isEqualTo("ال");
    }

    @Test
    void normalizeArabic_normalizesTaaMarbuta() {
        assertThat(SearchNormalizer.normalizeArabic("مدرسة"))
                .isEqualTo("مدرسه");
    }

    @Test
    void normalizeArabic_removesTatweel() {
        assertThat(SearchNormalizer.normalizeArabic("مـحمد"))
                .isEqualTo("محمد");
    }

    @Test
    void normalizeArabic_lowercasesAndTrims() {
        assertThat(SearchNormalizer.normalizeArabic("  أحمد Ali  "))
                .isEqualTo("احمد ali");
    }

    @Test
    void normalizeArabic_nullOrBlank_returnsEmpty() {
        assertThat(SearchNormalizer.normalizeArabic(null)).isEmpty();
        assertThat(SearchNormalizer.normalizeArabic("")).isEmpty();
        assertThat(SearchNormalizer.normalizeArabic("   ")).isEmpty();
    }

    @Test
    void normalizeArabic_mixedArabicAndLatin() {
        assertThat(SearchNormalizer.normalizeArabic("مُحمد Smith"))
                .isEqualTo("محمد smith");
    }

    @Test
    void normalizeArabic_combinedNormalizations() {
        assertThat(SearchNormalizer.normalizeArabic("أَحْمَدْ آلْ"))
                .isEqualTo("احمد ال");
    }

    // ── normalizePhone ──────────────────────────────────────────────────

    @Test
    void normalizePhone_canonicalElevenDigits_passthrough() {
        assertThat(SearchNormalizer.normalizePhone("01012345678"))
                .isEqualTo("01012345678");
    }

    @Test
    void normalizePhone_withSpaces_stripsThem() {
        assertThat(SearchNormalizer.normalizePhone("010 123 456 78"))
                .isEqualTo("01012345678");
    }

    @Test
    void normalizePhone_withDashes_stripsThem() {
        assertThat(SearchNormalizer.normalizePhone("010-123-456-78"))
                .isEqualTo("01012345678");
    }

    @Test
    void normalizePhone_internationalWithPlus20_stripsPrefix() {
        assertThat(SearchNormalizer.normalizePhone("+201012345678"))
                .isEqualTo("01012345678");
    }

    @Test
    void normalizePhone_internationalWith0020_stripsPrefix() {
        assertThat(SearchNormalizer.normalizePhone("00201012345678"))
                .isEqualTo("01012345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01012345678", "01112345678", "01212345678", "01512345678"})
    void normalizePhone_validPrefixes_accepted(String phone) {
        assertThat(SearchNormalizer.normalizePhone(phone))
                .isEqualTo(phone);
    }

    @Test
    void normalizePhone_invalidLength_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> SearchNormalizer.normalizePhone("0101234567"));
        assertThrows(IllegalArgumentException.class,
                () -> SearchNormalizer.normalizePhone("010123456789"));
    }

    @Test
    void normalizePhone_invalidPrefix_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> SearchNormalizer.normalizePhone("01612345678"));
    }

    @Test
    void normalizePhone_null_returnsNull() {
        assertNull(SearchNormalizer.normalizePhone(null));
    }

    @Test
    void normalizePhone_blank_returnsNull() {
        assertNull(SearchNormalizer.normalizePhone(""));
        assertNull(SearchNormalizer.normalizePhone("   "));
    }

    // ── normalizeNameForSearch ──────────────────────────────────────────

    @Test
    void normalizeNameForSearch_trimsAndNormalizesArabic() {
        assertThat(SearchNormalizer.normalizeNameForSearch("  أحمد محمد  "))
                .isEqualTo("احمد محمد");
    }

    @Test
    void normalizeNameForSearch_preservesLatin() {
        assertThat(SearchNormalizer.normalizeNameForSearch("John Doe"))
                .isEqualTo("john doe");
    }

    // ── escapeLikeWildcards ─────────────────────────────────────────────

    @Test
    void escapeLikeWildcards_escapesPercentAndUnderscore() {
        assertThat(SearchNormalizer.escapeLikeWildcards("100%_done"))
                .isEqualTo("100\\%\\_done");
    }

    @Test
    void escapeLikeWildcards_escapesBackslash() {
        assertThat(SearchNormalizer.escapeLikeWildcards("C:\\path"))
                .isEqualTo("C:\\\\path");
    }

    @Test
    void escapeLikeWildcards_noSpecialChars_unchanged() {
        assertThat(SearchNormalizer.escapeLikeWildcards("normal text"))
                .isEqualTo("normal text");
    }

    // ── normalizeForSearch ──────────────────────────────────────────────

    @Test
    void normalizeForSearch_combinesNormalizationAndEscaping() {
        assertThat(SearchNormalizer.normalizeForSearch("أحمد 100%"))
                .isEqualTo("احمد 100\\%");
    }

    @Test
    void normalizeForSearch_null_returnsEmpty() {
        assertThat(SearchNormalizer.normalizeForSearch(null)).isEmpty();
    }
}
