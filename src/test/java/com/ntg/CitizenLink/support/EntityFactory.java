package com.ntg.CitizenLink.support;

import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.entities.Category;
import com.ntg.CitizenLink.entities.Citizen;
import com.ntg.CitizenLink.entities.Department;
import com.ntg.CitizenLink.entities.StatusHistory;
import com.ntg.CitizenLink.enums.CaseStatus;
import com.ntg.CitizenLink.enums.CaseType;
import com.ntg.CitizenLink.enums.Channel;
import com.ntg.CitizenLink.enums.Priority;
import com.ntg.CitizenLink.enums.UserRole;
import com.ntg.CitizenLink.enums.WorkflowAction;

import java.util.UUID;

/**
 * Test fixture factory. Every generated entity gets globally unique
 * business-identifier values so unique constraints never collide,
 * even when several entities are created inside one test method.
 */
public final class EntityFactory {

    private EntityFactory() {}

    private static int seq = 0;

    private static synchronized String unique(String prefix) {
        seq++;
        return prefix + "-" + seq + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static AppUser appUser(UserRole role) {
        AppUser user = new AppUser();
        user.setUsername(unique("user"));
        user.setPasswordHash("$2a$10$0123456789abcdef0123456789abcdef0123456789abcdef");
        user.setDisplayName("User " + seq);
        user.setEmail(unique("mail") + "@test.gov");
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    public static Category category() {
        Category c = new Category();
        c.setCode(unique("CAT"));
        c.setNameEn("Category EN " + seq);
        c.setNameAr("Category AR " + seq);
        c.setActive(true);
        return c;
    }

    public static Department department() {
        Department d = new Department();
        d.setCode(unique("DEPT"));
        d.setNameEn("Department EN " + seq);
        d.setNameAr("Department AR " + seq);
        d.setActive(true);
        return d;
    }

    public static Citizen citizen(AppUser createdBy) {
        Citizen c = new Citizen();
        c.setFullName("Citizen " + seq);
        c.setNationalId(unique("NID"));
        c.setPhone(String.format("%011d", seq));
        c.setEmail(unique("citizen") + "@test.gov");
        c.setCreatedByUser(createdBy);
        return c;
    }

    public static Case aCase(Citizen citizen, Category category, Department department, AppUser createdBy) {
        Case c = new Case();
        c.setCaseNumber(unique("CASE"));
        c.setSubject("Subject " + seq);
        c.setDescription("Description " + seq);
        c.setType(CaseType.COMPLAINT);
        c.setPriority(Priority.MEDIUM);
        c.setStatus(CaseStatus.NEW);
        c.setChannel(Channel.WEB);
        c.setCitizen(citizen);
        c.setCategory(category);
        c.setDepartment(department);
        c.setCreatedByUser(createdBy);
        return c;
    }

    public static StatusHistory statusHistory(Case caseEntity, AppUser changedBy,
                                              CaseStatus from, CaseStatus to, WorkflowAction action) {
        StatusHistory h = new StatusHistory();
        h.setCaseEntity(caseEntity);
        h.setChangedByUser(changedBy);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setAction(action);
        return h;
    }
}
