package com.smartcity360.ejb.security;

import com.smartcity360.common.security.*;
import com.smartcity360.ejb.persistence.model.UserEntity;
import com.smartcity360.ejb.persistence.repository.*;
import jakarta.annotation.Resource;
import jakarta.annotation.security.*;
import jakarta.ejb.*;
import jakarta.inject.Inject;

import java.util.*;

@Stateless
@DeclareRoles({"ADMIN", "CITY_MANAGER", "LOCAL_STAFF", "RESIDENT", "SUPERVISOR"})
public class CallerSecurityServiceBean implements CallerSecurityService {
    @Resource
    SessionContext session;
    @Inject
    UserRepository users;
    @Inject
    UserRoleRepository userRoles;
    @Inject
    SecurityAuditService audit;

    @RolesAllowed({"ADMIN", "CITY_MANAGER", "LOCAL_STAFF", "RESIDENT", "SUPERVISOR"})
    public CurrentUserView currentUser() {
        String principal = session.getCallerPrincipal().getName();
        UserEntity u = users.findByEmail(principal).orElseThrow(() -> new EJBAccessException("Authenticated user no longer exists"));
        if (u.getStatus() != UserEntity.Status.ACTIVE) throw new EJBAccessException("Account is not active");
        Set<String> roles = new TreeSet<>();
        userRoles.findByUserId(u.getId()).forEach(r -> roles.add(r.getId().getRoleCode()));
        for (String role : List.of("ADMIN", "CITY_MANAGER", "LOCAL_STAFF", "RESIDENT", "SUPERVISOR"))
            if (roles.contains(role) != session.isCallerInRole(role))
                throw new EJBAccessException("Session permissions changed; reauthentication required");
        return new CurrentUserView(u.getId(), u.getFullName(), u.getEmail(), roles);
    }

    @RolesAllowed({"ADMIN", "CITY_MANAGER", "LOCAL_STAFF", "RESIDENT", "SUPERVISOR"})
    public void recordLogout() {
        CurrentUserView u = currentUser();
        audit.record("AUTH_LOGOUT", u.id(), "SUCCESS", null);
    }

    @RolesAllowed("ADMIN")
    public String adminProbe() {
        CurrentUserView user = currentUser();
        if (!user.roles().contains("ADMIN")) throw new EJBAccessException("ADMIN role required");
        return session.getCallerPrincipal().getName();
    }

    public boolean isCallerInRole(String role) {
        return session.isCallerInRole(role);
    }
}
