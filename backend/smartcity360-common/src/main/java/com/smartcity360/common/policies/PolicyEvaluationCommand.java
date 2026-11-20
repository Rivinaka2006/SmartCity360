package com.smartcity360.common.policies;

import java.util.Map;

public record PolicyEvaluationCommand(Map<String, Object> context) {
    public PolicyEvaluationCommand { context = context == null ? Map.of() : Map.copyOf(context); }
}
