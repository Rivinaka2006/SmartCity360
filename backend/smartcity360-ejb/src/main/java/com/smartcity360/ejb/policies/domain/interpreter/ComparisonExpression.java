package com.smartcity360.ejb.policies.domain.interpreter;

import java.util.List;
import java.util.Objects;

public record ComparisonExpression(Operand left, ComparisonOperator operator, Operand right) implements Expression {
    public ComparisonExpression {
        Objects.requireNonNull(left);
        Objects.requireNonNull(operator);
        Objects.requireNonNull(right);
    }

    @Override
    public EvaluationResult interpret(EvaluationContext context) {
        TypedValue a = left.resolve(context);
        TypedValue b = right.resolve(context);
        if (a.type() != b.type()) throw new DslValidationException("CONTEXT_TYPE_MISMATCH", "Operand types differ", -1);
        int comparison = switch (a.type()) {
            case NUMERIC -> a.number().compareTo(b.number());
            case STRING -> ((String) a.value()).compareTo((String) b.value());
            case BOOLEAN -> Boolean.compare((Boolean) a.value(), (Boolean) b.value());
        };
        boolean outcome = switch (operator) {
            case GREATER -> comparison > 0;
            case GREATER_EQUAL -> comparison >= 0;
            case LESS -> comparison < 0;
            case LESS_EQUAL -> comparison <= 0;
            case EQUAL -> comparison == 0;
            case NOT_EQUAL -> comparison != 0;
        };
        return new EvaluationResult(outcome, new EvaluationExplanation("COMPARE", true, outcome,
                left.display() + " " + operator.symbol() + " " + right.display(), List.of()));
    }
}
