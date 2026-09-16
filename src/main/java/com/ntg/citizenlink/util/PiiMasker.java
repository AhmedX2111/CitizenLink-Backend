package com.ntg.citizenlink.util;

public final class PiiMasker {

    private PiiMasker() {}

    public static String maskNationalId(String value) {
        if (value == null || value.isBlank()) return value;
        String digits = value.replaceAll("\\s", "");
        if (digits.length() <= 4) return digits;
        int visiblePrefix = 3;
        int visibleSuffix = 4;
        if (digits.length() <= visiblePrefix + visibleSuffix) {
            return digits.substring(0, 1) + "****";
        }
        return digits.substring(0, visiblePrefix)
                + "****"
                + digits.substring(digits.length() - visibleSuffix);
    }

    public static String maskPhone(String value) {
        if (value == null || value.isBlank()) return value;
        String digits = value.replaceAll("\\D", "");
        if (digits.length() <= 4) return digits;
        int visiblePrefix = 3;
        int visibleSuffix = 4;
        if (digits.length() <= visiblePrefix + visibleSuffix) {
            return digits.substring(0, 1) + "****";
        }
        return digits.substring(0, visiblePrefix)
                + "****"
                + digits.substring(digits.length() - visibleSuffix);
    }

    public static String maskEmail(String value) {
        if (value == null || value.isBlank()) return value;
        int atIndex = value.indexOf('@');
        if (atIndex <= 0) return "****";
        if (atIndex == 1) return value.substring(0, 1) + "****" + value.substring(atIndex);
        return value.substring(0, 1) + "****" + value.substring(atIndex);
    }
}
