package com.ntg.citizenlink.util;

import com.ntg.citizenlink.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static com.ntg.citizenlink.util.MaskingContext.*;
import static com.ntg.citizenlink.util.MaskingLevel.*;
import static org.assertj.core.api.Assertions.assertThat;

class MaskingPolicyTest {

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void fullName_isAlwaysFull_forAnyRole_andAnyContext(UserRole role) {
        assertThat(MaskingPolicy.forField(role, "fullName", SEARCH_RESULTS)).isEqualTo(FULL);
        assertThat(MaskingPolicy.forField(role, "fullName", DETAIL_VIEW)).isEqualTo(FULL);
        assertThat(MaskingPolicy.forField(role, "fullName", CASE_LIST)).isEqualTo(FULL);
        assertThat(MaskingPolicy.forField(role, "fullName", CSV_EXPORT)).isEqualTo(FULL);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void unknownField_isFull_byDefault_forKnownRoles(UserRole role) {
        if (role == UserRole.AGENT) {
            assertThat(MaskingPolicy.forField(role, "unknownField", DETAIL_VIEW)).isEqualTo(MASKED);
        } else {
            assertThat(MaskingPolicy.forField(role, "unknownField", DETAIL_VIEW)).isEqualTo(FULL);
        }
    }

    // ── SEARCH_RESULTS ───────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void searchResults_nationalId_isMaskedForAllRoles(UserRole role) {
        assertThat(MaskingPolicy.forField(role, "nationalId", SEARCH_RESULTS)).isEqualTo(MASKED);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void searchResults_phone_isMaskedForAllRoles(UserRole role) {
        assertThat(MaskingPolicy.forField(role, "phone", SEARCH_RESULTS)).isEqualTo(MASKED);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void searchResults_email_isMaskedForAllRoles(UserRole role) {
        assertThat(MaskingPolicy.forField(role, "email", SEARCH_RESULTS)).isEqualTo(MASKED);
    }

    // ── DETAIL_VIEW ──────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "SUPERVISOR", "HANDLER"})
    void detailView_sensitiveFields_areFull_forAdminSupervisorHandler(UserRole role) {
        for (String field : List.of("nationalId", "phone", "email")) {
            assertThat(MaskingPolicy.forField(role, field, DETAIL_VIEW))
                    .as("field=%s role=%s", field, role)
                    .isEqualTo(FULL);
        }
    }

    @Test
    void detailView_agent_getsMaskedSensitiveFields() {
        for (String field : List.of("nationalId", "phone", "email")) {
            assertThat(MaskingPolicy.forField(UserRole.AGENT, field, DETAIL_VIEW))
                    .as("field=%s", field)
                    .isEqualTo(MASKED);
        }
    }

    // ── CASE_LIST ────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "SUPERVISOR", "HANDLER"})
    void caseList_sensitiveFields_areFull_forAdminSupervisorHandler(UserRole role) {
        for (String field : List.of("nationalId", "phone", "email")) {
            assertThat(MaskingPolicy.forField(role, field, CASE_LIST))
                    .as("field=%s role=%s", field, role)
                    .isEqualTo(FULL);
        }
    }

    @Test
    void caseList_agent_getsMaskedSensitiveFields() {
        for (String field : List.of("nationalId", "phone", "email")) {
            assertThat(MaskingPolicy.forField(UserRole.AGENT, field, CASE_LIST))
                    .as("field=%s", field)
                    .isEqualTo(MASKED);
        }
    }

    // ── CSV_EXPORT ───────────────────────────────────────────────────────

    @Test
    void csvExport_admin_getsFullSensitiveFields() {
        for (String field : List.of("nationalId", "phone", "email")) {
            assertThat(MaskingPolicy.forField(UserRole.ADMIN, field, CSV_EXPORT))
                    .as("field=%s", field)
                    .isEqualTo(FULL);
        }
    }

    @Test
    void csvExport_supervisor_getsMaskedSensitiveFields() {
        for (String field : List.of("nationalId", "phone", "email")) {
            assertThat(MaskingPolicy.forField(UserRole.SUPERVISOR, field, CSV_EXPORT))
                    .as("field=%s", field)
                    .isEqualTo(MASKED);
        }
    }

    @Test
    void csvExport_handler_returnsNone() {
        assertThat(MaskingPolicy.forField(UserRole.HANDLER, "nationalId", CSV_EXPORT))
                .isEqualTo(NONE);
    }

    @Test
    void csvExport_agent_returnsNone() {
        assertThat(MaskingPolicy.forField(UserRole.AGENT, "nationalId", CSV_EXPORT))
                .isEqualTo(NONE);
    }
}
