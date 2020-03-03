package edu.uc.ltigradebook.util;

import edu.ksu.lti.launch.model.InstitutionRole;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class RoleChecker {

    private final List<InstitutionRole> validRoles;

    public RoleChecker(List<InstitutionRole> validRoles) {
        this.validRoles = validRoles;
    }

    public boolean roleAllowed(List<InstitutionRole> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            log.warn("Found empty role list - this shouldn't be happening");
            return false;
        }
        return validRoles.stream().anyMatch(userRoles::contains);
    }

}
