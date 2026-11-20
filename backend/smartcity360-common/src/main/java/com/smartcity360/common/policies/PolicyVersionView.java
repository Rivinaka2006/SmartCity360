package com.smartcity360.common.policies;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PolicyVersionView(UUID id, UUID policyId, int versionNo, String dslVersion,
                                String dslExpression, boolean valid, String validationError,
                                boolean active, UUID createdBy, OffsetDateTime createdAt) { }
