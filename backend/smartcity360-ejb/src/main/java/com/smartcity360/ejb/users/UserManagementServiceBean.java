package com.smartcity360.ejb.users;

import com.smartcity360.common.users.ManagedUserView;
import com.smartcity360.common.users.UserCreateCommand;
import com.smartcity360.common.users.UserManagementException;
import com.smartcity360.common.users.UserManagementService;
import com.smartcity360.common.users.UserRoleUpdateCommand;
import com.smartcity360.common.users.UserStatusUpdateCommand;
import com.smartcity360.ejb.persistence.model.RoleEntity;
import com.smartcity360.ejb.persistence.model.UserEntity;
import com.smartcity360.ejb.persistence.model.UserRoleEntity;
import com.smartcity360.ejb.persistence.model.UserRoleId;
import com.smartcity360.ejb.persistence.repository.RoleRepository;
import com.smartcity360.ejb.persistence.repository.UserRepository;
import com.smartcity360.ejb.persistence.repository.UserRoleRepository;
import com.smartcity360.ejb.security.PasswordHasher;
import com.smartcity360.ejb.security.SecurityAuditService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Stateless
@DeclareRoles("ADMIN")
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class UserManagementServiceBean implements UserManagementService {
    private static final String ADMIN = "ADMIN";
    private static final String TEST_PREFIX = "[TEST] ";

    @Inject
    UserRepository users;
    @Inject
    UserRoleRepository userRoles;
    @Inject
    RoleRepository roles;
    @Inject
    PasswordHasher passwordHasher;
    @Inject
    SecurityAuditService audit;
    @Resource
    SessionContext session;

    @Override
    @RolesAllowed("ADMIN")
    public List<ManagedUserView> list(String search, int offset, int limit) {
        validatePage(offset, limit);
        return users.findBySearch(search, offset, limit).stream().map(this::view).toList();
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ManagedUserView create(UserCreateCommand command) {
        if (command == null) {
            throw error("REQUEST_INVALID");
        }
        UserEntity actor = requireActor();
        String fullName = requiredText(command.fullName(), 120, "FULL_NAME_INVALID");
        String email = normalizeEmail(command.email());
        String roleCode = requiredRole(command.role());
        char[] password = command.password() == null ? new char[0] : command.password().toCharArray();
        try {
            validatePassword(password);
            if (users.findByEmail(email).isPresent()) {
                throw error("EMAIL_ALREADY_EXISTS");
            }
            UserEntity user = new UserEntity();
            user.setId(UUID.randomUUID());
            user.setFullName(command.integrationTestAccount() && !fullName.startsWith(TEST_PREFIX)
                    ? TEST_PREFIX + fullName
                    : fullName);
            user.setEmail(email);
            user.setPasswordHash(passwordHasher.hash(password));
            user.setStatus(UserEntity.Status.ACTIVE);
            OffsetDateTime now = OffsetDateTime.now();
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            users.persist(user);
            users.flush();
            assignRole(user, roleCode);
            audit.recordApplicationEvent("USER_ACCOUNT_CREATED", actor.getId(), "USER", user.getId(),
                    "{\"role\":\"" + roleCode + "\",\"integrationTestAccount\":" + command.integrationTestAccount() + "}");
            return view(user);
        } catch (PersistenceException exception) {
            throw error("EMAIL_ALREADY_EXISTS");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ManagedUserView updateRoles(UUID userId, UserRoleUpdateCommand command) {
        if (command == null || command.roles() == null || command.roles().isEmpty()) {
            throw error("ROLE_REQUIRED");
        }
        UserEntity actor = requireActor();
        UserEntity user = requireUser(userId);
        if (actor.getId().equals(user.getId())) {
            throw error("SELF_LOCKOUT_PREVENTED");
        }
        Set<String> requestedRoles = new LinkedHashSet<>();
        for (String role : command.roles()) {
            requestedRoles.add(requiredRole(role));
        }
        boolean removesAdmin = userRoles.hasRole(user.getId(), ADMIN) && !requestedRoles.contains(ADMIN);
        users.lockActiveAdminInvariant();
        if (removesAdmin && user.getStatus() == UserEntity.Status.ACTIVE && users.countActiveUsersWithRole(ADMIN) <= 1) {
            throw error("LAST_ACTIVE_ADMIN");
        }
        userRoles.removeAllForUser(user.getId());
        for (String role : requestedRoles) {
            assignRole(user, role);
        }
        user.setUpdatedAt(OffsetDateTime.now());
        users.update(user);
        audit.recordApplicationEvent("USER_ROLES_UPDATED", actor.getId(), "USER", user.getId(),
                "{\"roleCount\":" + requestedRoles.size() + "}");
        return view(user);
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ManagedUserView updateStatus(UUID userId, UserStatusUpdateCommand command) {
        if (command == null) {
            throw error("REQUEST_INVALID");
        }
        UserEntity actor = requireActor();
        UserEntity user = requireUser(userId);
        UserEntity.Status status = status(command.status());
        if (actor.getId().equals(user.getId()) && status != UserEntity.Status.ACTIVE) {
            throw error("SELF_LOCKOUT_PREVENTED");
        }
        boolean disablesActiveAdmin = user.getStatus() == UserEntity.Status.ACTIVE
                && status != UserEntity.Status.ACTIVE
                && userRoles.hasRole(user.getId(), ADMIN);
        users.lockActiveAdminInvariant();
        if (disablesActiveAdmin && users.countActiveUsersWithRole(ADMIN) <= 1) {
            throw error("LAST_ACTIVE_ADMIN");
        }
        user.setStatus(status);
        user.setUpdatedAt(OffsetDateTime.now());
        users.update(user);
        audit.recordApplicationEvent("USER_STATUS_UPDATED", actor.getId(), "USER", user.getId(),
                "{\"status\":\"" + status.name() + "\"}");
        return view(user);
    }

    private UserEntity requireActor() {
        String email = session.getCallerPrincipal().getName();
        UserEntity actor = users.findByEmail(email).orElseThrow(() -> error("FORBIDDEN"));
        if (actor.getStatus() != UserEntity.Status.ACTIVE || !userRoles.hasRole(actor.getId(), ADMIN)) {
            throw error("FORBIDDEN");
        }
        return actor;
    }

    private UserEntity requireUser(UUID userId) {
        if (userId == null) {
            throw error("USER_NOT_FOUND");
        }
        return users.findById(userId).orElseThrow(() -> error("USER_NOT_FOUND"));
    }

    private String requiredRole(String value) {
        String role = requiredText(value, 30, "ROLE_INVALID").toUpperCase(Locale.ROOT);
        if (roles.findById(role).isEmpty()) {
            throw error("ROLE_INVALID");
        }
        return role;
    }

    private void assignRole(UserEntity user, String roleCode) {
        RoleEntity role = roles.findById(roleCode).orElseThrow(() -> error("ROLE_INVALID"));
        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setId(new UserRoleId(user.getId(), roleCode));
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedAt(OffsetDateTime.now());
        userRoles.persist(userRole);
    }

    private ManagedUserView view(UserEntity user) {
        Set<String> assignedRoles = new LinkedHashSet<>();
        userRoles.findByUserId(user.getId()).forEach(userRole -> assignedRoles.add(userRole.getId().getRoleCode()));
        return new ManagedUserView(user.getId(), user.getFullName(), user.getEmail(), user.getStatus().name(),
                Set.copyOf(assignedRoles), user.getFullName().startsWith(TEST_PREFIX), user.getCreatedAt(), user.getUpdatedAt());
    }

    private static String normalizeEmail(String value) {
        String email = requiredText(value, 255, "EMAIL_INVALID").toLowerCase(Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw error("EMAIL_INVALID");
        }
        return email;
    }

    private static String requiredText(String value, int maximumLength, String code) {
        if (value == null) {
            throw error(code);
        }
        String text = value.strip();
        if (text.isEmpty() || text.length() > maximumLength) {
            throw error(code);
        }
        return text;
    }

    private static void validatePassword(char[] password) {
        if (password.length < 14) {
            throw error("PASSWORD_INVALID");
        }
    }

    private static UserEntity.Status status(String value) {
        try {
            return UserEntity.Status.valueOf(requiredText(value, 16, "STATUS_INVALID").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error("STATUS_INVALID");
        }
    }

    private static void validatePage(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) {
            throw error("PAGINATION_INVALID");
        }
    }

    private static UserManagementException error(String code) {
        return new UserManagementException(code);
    }
}
