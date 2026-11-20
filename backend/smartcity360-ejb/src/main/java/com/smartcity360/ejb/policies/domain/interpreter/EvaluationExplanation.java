package com.smartcity360.ejb.policies.domain.interpreter;

import java.util.List;

public record EvaluationExplanation(String operator, boolean evaluated, Boolean outcome, String detail,
                                    List<EvaluationExplanation> children) {
    public EvaluationExplanation {
        children = List.copyOf(children);
    }

    public static EvaluationExplanation leaf(String operator, boolean outcome, String detail) {
        return new EvaluationExplanation(operator, true, outcome, detail, List.of());
    }

    public static EvaluationExplanation skipped(String detail) {
        return new EvaluationExplanation("SKIPPED", false, null, detail, List.of());
    }
}
