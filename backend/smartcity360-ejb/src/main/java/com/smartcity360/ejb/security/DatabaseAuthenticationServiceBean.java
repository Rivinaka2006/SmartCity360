package com.smartcity360.ejb.security;

import com.smartcity360.common.security.*;
import com.smartcity360.ejb.persistence.model.UserEntity;
import com.smartcity360.ejb.persistence.repository.*;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Stateless
public class DatabaseAuthenticationServiceBean implements AuthenticationGateway {
    @Inject
    UserRepository users;
    @Inject
    UserRoleRepository userRoles;
    @Inject
    PasswordHasher passwords;
    @Inject
    SecurityAuditService audit;

    public AuthenticationResult authenticate(String email, char[] password) {
        String normalized = normalize(email);
        String subject = fingerprint(normalized);
        Optional<UserEntity> found = users.findByEmail(normalized);
        if (found.isEmpty() || found.get().getStatus() != UserEntity.Status.ACTIVE || !passwords.verify(password, found.get().getPasswordHash())) {
            audit.record("AUTH_LOGIN_FAILED", null, "FAILED", subject);
            return AuthenticationResult.failed();
        }
        UserEntity user = found.get();
        if (passwords.needsUpgrade(user.getPasswordHash())) {
            user.setPasswordHash(passwords.hash(password));
            users.update(user);
        }
        Set<String> roles = new TreeSet<>();
        userRoles.findByUserId(user.getId()).forEach(r -> roles.add(r.getId().getRoleCode()));
        audit.record("AUTH_LOGIN_SUCCESS", user.getId(), "SUCCESS", subject);
        return new AuthenticationResult(true, user.getId(), normalize(user.getEmail()), roles);
    }

    public void recordRejectedAttempt(String identifier) {
        audit.record("AUTH_LOGIN_THROTTLED", null, "REJECTED", fingerprint(normalize(identifier)));
    }

    public void recordAuthorizationDenied(String identifier) {
        audit.record("AUTHORIZATION_DENIED", null, "DENIED", fingerprint(normalize(identifier)));
    }

    static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
