package com.ntg.citizenlink.util;

public final class SearchNormalizer {

    private static final char LIKE_ESCAPE_CHAR = '\\';

    private SearchNormalizer() {}

    /** Arabic character normalization: tashkeel, alef variants, taa marbuta, tatweel */
    public static String normalizeArabic(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String s = input.toLowerCase();
        // Remove tashkeel (diacritics U+064B–U+065F, U+0670)
        s = s.replaceAll("[\\u064B\\u064C\\u064D\\u064E\\u064F\\u0650\\u0651\\u0652\\u0653\\u0670]", "");
        // Normalize alef variants: أ إ آ → ا
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا');
        // Normalize taa marbuta: ة → ه
        s = s.replace('ة', 'ه');
        // Remove tatweel
        s = s.replaceAll("\u0640", "");
        return s.trim();
    }

    /** Egyptian phone normalization */
    public static String normalizePhone(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        // 1. Strip all non-digits
        String digits = input.replaceAll("\\D", "");
        // 2. Handle 0020 prefix (international dialing from Egypt)
        if (digits.startsWith("00") && digits.length() > 11) {
            digits = digits.substring(2);
        }
        // 3. Handle +20 prefix: after stripping +, digits start with 20...
        //    Strip leading 2 to get local format (20XXXXXXXXXX → 0XXXXXXXXXX)
        if (digits.length() == 12 && digits.startsWith("2")) {
            digits = digits.substring(1);
        }
        // 4. Validate length
        if (digits.length() != 11) {
            throw new IllegalArgumentException(
                    "Phone must be 11 digits after normalization, got: " + digits);
        }
        // 5. Validate Egyptian mobile prefix
        if (!digits.matches("^(010|011|012|015)\\d{8}$")) {
            throw new IllegalArgumentException(
                    "Phone must start with 010, 011, 012, or 015");
        }
        return digits;
    }

    /** Name normalization: trim + Arabic normalize */
    public static String normalizeNameForSearch(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return normalizeArabic(input.trim());
    }

    /** LIKE-safe escaping to prevent wildcard injection */
    public static String escapeLikeWildcards(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /** All-in-one: normalize name + escape for LIKE */
    public static String normalizeForSearch(String input) {
        return escapeLikeWildcards(normalizeNameForSearch(input));
    }
}
