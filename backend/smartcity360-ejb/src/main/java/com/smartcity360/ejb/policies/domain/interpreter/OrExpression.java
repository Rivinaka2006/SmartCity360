package com.smartcity360.ejb.policies.domain.interpreter;

import java.util.List;
import java.util.Objects;

public record OrExpression(Expression left, Expression right) implements Expression {
    public OrExpression {
        Objects.requireNonNull(left);
        Objects.requireNonNull(right);
    }

    @Override
    public EvaluationResult interpret(EvaluationContext context) {
        EvaluationResult first = left.interpret(context);
        if (first.outcome()) return new EvaluationResult(true, new EvaluationExplanation("OR", true, true,
                "Right branch skipped after TRUE", List.of(first.explanation(), EvaluationExplanation.skipped("OR short-circuit"))));
        EvaluationResult second = right.interpret(context);
        return new EvaluationResult(second.outcome(), new EvaluationExplanation("OR", true, second.outcome(),
                "Both operands evaluated", List.of(first.explanation(), second.explanation())));
    }
}
