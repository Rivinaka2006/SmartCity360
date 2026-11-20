package com.smartcity360.common.policies;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PolicyEvaluationView(UUID id, UUID policyId, UUID policyVersionId, int versionNo,
                                   UUID evaluatedBy, String status, Boolean result, String errorCode,
                                   int durationMs, OffsetDateTime evaluatedAt, String explanationJson) { }
