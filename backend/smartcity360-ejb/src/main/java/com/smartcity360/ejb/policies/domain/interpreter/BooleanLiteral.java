package com.smartcity360.ejb.policies.domain.interpreter;

public record BooleanLiteral(boolean value) implements Operand {
    @Override
    public TypedValue resolve(EvaluationContext context) {
        return TypedValue.bool(value);
    }

    @Override
    public ValueType type() {
        return ValueType.BOOLEAN;
    }

    @Override
    public String display() {
        return Boolean.toString(value);
    }
}
