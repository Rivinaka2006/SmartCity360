package com.smartcity360.common.users;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record ManagedUserView(
        UUID id,
        String fullName,
        String email,
        String status,
        Set<String> roles,
        boolean integrationTestAccount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
