package com.smartcity360.ejb.policies.domain.interpreter;

import java.util.List;
import java.util.Objects;

public record NotExpression(Expression expression) implements Expression {
    public NotExpression {
        Objects.requireNonNull(expression);
    }

    @Override
    public EvaluationResult interpret(EvaluationContext context) {
        EvaluationResult child = expression.interpret(context);
        boolean outcome = !child.outcome();
        return new EvaluationResult(outcome, new EvaluationExplanation("NOT", true, outcome,
                "Logical negation", List.of(child.explanation())));
    }
}
