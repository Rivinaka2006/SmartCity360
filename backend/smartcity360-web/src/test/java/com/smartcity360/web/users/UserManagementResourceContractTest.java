package com.smartcity360.web.users;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserManagementResourceContractTest {
    @Test
    void resourceIsAdminOnlyAtTheRestBoundary() {
        assertEquals("/users", UserManagementResource.class.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMIN"), Set.of(UserManagementResource.class.getAnnotation(RolesAllowed.class).value()));
    }
}
