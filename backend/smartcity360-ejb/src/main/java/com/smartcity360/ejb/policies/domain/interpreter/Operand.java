package com.smartcity360.ejb.policies.domain.interpreter;

public sealed interface Operand permits VariableOperand, NumericLiteral, StringLiteral, BooleanLiteral {
    TypedValue resolve(EvaluationContext context);

    ValueType type();

    String display();
}
