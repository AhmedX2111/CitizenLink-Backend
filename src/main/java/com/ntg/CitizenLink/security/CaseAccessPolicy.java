package com.ntg.CitizenLink.security;

import com.ntg.CitizenLink.entities.AppUser;
import com.ntg.CitizenLink.entities.Case;
import com.ntg.CitizenLink.enums.UserRole;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for "who may see/act on this case."
 *
 * Visibility rule (confirmed for US-14/US-17):
 *   - ADMIN, SUPERVISOR -> any case
 *   - HANDLER           -> only cases assigned to them
 *   - AGENT              -> only cases they created
 *
 * This is intentionally separate from CaseWorkflowService (which decides
 * WHICH actions are valid for a status+role). This class only answers
 * "can this user see/touch this case at all" — the workflow service then
 * layers on top of a positive answer here.
 */
@Component
public class CaseAccessPolicy {

    public boolean canView(Case caseEntity, AppUser user) {
        UserRole role = user.getRole();

        if (role == UserRole.ADMIN || role == UserRole.SUPERVISOR) {
            return true;
        }

        if (role == UserRole.HANDLER) {
            return caseEntity.getAssignedToUser() != null
                    && caseEntity.getAssignedToUser().getId().equals(user.getId());
        }

        // AGENT (and any other role) — creator only
        return caseEntity.getCreatedByUser().getId().equals(user.getId());
    }
}