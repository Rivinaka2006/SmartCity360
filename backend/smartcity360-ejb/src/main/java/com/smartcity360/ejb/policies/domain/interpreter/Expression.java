package com.smartcity360.ejb.policies.domain.interpreter;

public sealed interface Expression permits ComparisonExpression, AndExpression, OrExpression, NotExpression {
    EvaluationResult interpret(EvaluationContext context);
}
