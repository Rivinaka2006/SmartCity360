package com.smartcity360.ejb.users;

import com.smartcity360.common.users.UserManagementService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagementServiceContractTest {
    @Test
    void userManagementIsAnAdminOnlyRequiredTransactionBoundary() throws Exception {
        Class<?> type = UserManagementServiceBean.class;
        assertTrue(UserManagementService.class.isAssignableFrom(type));
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() != type || method.getName().equals("view")) {
                continue;
            }
            RolesAllowed roles = method.getAnnotation(RolesAllowed.class);
            if (roles != null) {
                assertEquals(Set.of("ADMIN"), Set.of(roles.value()));
            }
        }
        assertEquals(TransactionAttributeType.REQUIRED,
                type.getMethod("create", com.smartcity360.common.users.UserCreateCommand.class)
                        .getAnnotation(TransactionAttribute.class).value());
        assertEquals(TransactionAttributeType.REQUIRED,
                type.getMethod("updateRoles", java.util.UUID.class,
                                com.smartcity360.common.users.UserRoleUpdateCommand.class)
                        .getAnnotation(TransactionAttribute.class).value());
        assertEquals(TransactionAttributeType.REQUIRED,
                type.getMethod("updateStatus", java.util.UUID.class,
                                com.smartcity360.common.users.UserStatusUpdateCommand.class)
                        .getAnnotation(TransactionAttribute.class).value());
    }
}
