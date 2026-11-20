package com.smartcity360.ejb.policies.domain.interpreter;

import java.util.Objects;

public record EvaluationResult(boolean outcome, EvaluationExplanation explanation) {
    public EvaluationResult {
        Objects.requireNonNull(explanation);
    }
}
