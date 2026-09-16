package com.ntg.citizenlink.service.mapper;

import com.ntg.citizenlink.dto.agent.response.CaseResponse;
import com.ntg.citizenlink.entities.Case;
import com.ntg.citizenlink.entities.Category;
import com.ntg.citizenlink.entities.Citizen;
import com.ntg.citizenlink.enums.CaseStatus;
import com.ntg.citizenlink.enums.CaseType;
import com.ntg.citizenlink.enums.Channel;
import com.ntg.citizenlink.enums.Priority;
import com.ntg.citizenlink.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CaseMapper}.
 *
 * Regression for L-03: citizenPhone is populated from the citizen block, so it
 * is set even when the category is null and never dereferences getCitizen()
 * without the citizen null guard.
 */
class CaseMapperTest {

    private final CaseMapper mapper = new CaseMapper();

    @Test
    void citizenPhone_isPopulated_whenCategoryIsNull() {
        CaseResponse r = mapper.toResponse(caseWith(citizen(), null), UserRole.ADMIN);

        assertThat(r.getCitizenPhone()).isEqualTo("0100000000");
        assertThat(r.getCategoryId()).isNull();
    }

    @Test
    void citizenPhone_isPopulated_whenCategoryPresent() {
        CaseResponse r = mapper.toResponse(caseWith(citizen(), category()), UserRole.ADMIN);

        assertThat(r.getCitizenPhone()).isEqualTo("0100000000");
        assertThat(r.getCategoryId()).isNotNull();
    }

    @Test
    void citizenPhone_isNull_whenCitizenIsNull_evenWithCategoryPresent() {
        CaseResponse r = mapper.toResponse(caseWith(null, category()), UserRole.ADMIN);

        assertThat(r.getCitizenPhone()).isNull();
        assertThat(r.getCategoryId()).isNotNull();
    }

    @Test
    void citizenNationalId_isMasked_for_AGENT() {
        CaseResponse r = mapper.toResponse(caseWith(citizen(), null), UserRole.AGENT);

        assertThat(r.getCitizenNationalId()).isEqualTo("123****3456");
    }

    @Test
    void citizenPhone_isMasked_for_AGENT() {
        CaseResponse r = mapper.toResponse(caseWith(citizen(), null), UserRole.AGENT);

        assertThat(r.getCitizenPhone()).isEqualTo("010****0000");
    }

    @Test
    void citizenNationalId_isFull_for_ADMIN() {
        CaseResponse r = mapper.toResponse(caseWith(citizen(), null), UserRole.ADMIN);

        assertThat(r.getCitizenNationalId()).isEqualTo("1234567890123456");
    }

    private Case caseWith(Citizen citizen, Category category) {
        Case c = new Case();
        c.setId(UUID.randomUUID());
        c.setCaseNumber("CASE-2026-0001");
        c.setSubject("Broken tap");
        c.setDescription("Leak in kitchen");
        c.setType(CaseType.REQUEST);
        c.setPriority(Priority.HIGH);
        c.setStatus(CaseStatus.IN_PROGRESS);
        c.setChannel(Channel.PHONE);
        c.setCitizen(citizen);
        c.setCategory(category);
        return c;
    }

    private Citizen citizen() {
        Citizen ci = new Citizen();
        ci.setId(UUID.randomUUID());
        ci.setFullName("John Doe");
        ci.setNationalId("1234567890123456");
        ci.setPhone("0100000000");
        return ci;
    }

    private Category category() {
        Category cat = new Category();
        cat.setId(UUID.randomUUID());
        cat.setNameEn("Water");
        cat.setNameAr("مياه");
        return cat;
    }
}