package com.ntg.citizenlink.util;

import com.ntg.citizenlink.enums.UserRole;

public final class MaskingPolicy {

    private MaskingPolicy() {}

    public static MaskingLevel forField(UserRole role, String field, MaskingContext context) {
        if (field == null) return MaskingLevel.NONE;
        String normalizedField = field.toLowerCase();

        if (isFullNameField(normalizedField)) {
            return MaskingLevel.FULL;
        }

        switch (context) {
            case SEARCH_RESULTS -> {
                return MaskingLevel.MASKED;
            }
            case DETAIL_VIEW, CASE_LIST -> {
                if (role == UserRole.ADMIN
                        || role == UserRole.SUPERVISOR
                        || role == UserRole.HANDLER) {
                    return MaskingLevel.FULL;
                }
                return MaskingLevel.MASKED;
            }
            case CSV_EXPORT -> {
                if (role == UserRole.ADMIN) {
                    return MaskingLevel.FULL;
                }
                if (role == UserRole.SUPERVISOR) {
                    return MaskingLevel.MASKED;
                }
                return MaskingLevel.NONE;
            }
            default -> {
                if (role == UserRole.ADMIN
                        || role == UserRole.SUPERVISOR
                        || role == UserRole.HANDLER) {
                    return MaskingLevel.FULL;
                }
                return MaskingLevel.MASKED;
            }
        }
    }

    private static boolean isFullNameField(String field) {
        return field.contains("fullname")
                || field.contains("full_name")
                || field.contains("displayname")
                || field.contains("display_name");
    }
}
