package com.smartcity360.ejb.policies.domain.interpreter;

import java.util.List;
import java.util.Objects;

public record AndExpression(Expression left, Expression right) implements Expression {
    public AndExpression {
        Objects.requireNonNull(left);
        Objects.requireNonNull(right);
    }

    @Override
    public EvaluationResult interpret(EvaluationContext context) {
        EvaluationResult first = left.interpret(context);
        if (!first.outcome()) return new EvaluationResult(false, new EvaluationExplanation("AND", true, false,
                "Right branch skipped after FALSE", List.of(first.explanation(), EvaluationExplanation.skipped("AND short-circuit"))));
        EvaluationResult second = right.interpret(context);
        return new EvaluationResult(second.outcome(), new EvaluationExplanation("AND", true, second.outcome(),
                "Both operands evaluated", List.of(first.explanation(), second.explanation())));
    }
}
