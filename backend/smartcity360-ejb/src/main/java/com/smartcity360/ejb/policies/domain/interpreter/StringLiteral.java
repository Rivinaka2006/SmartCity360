package com.smartcity360.ejb.policies.domain.interpreter;

import java.util.Objects;

public record StringLiteral(String value) implements Operand {
    public StringLiteral {
        Objects.requireNonNull(value);
    }

    @Override
    public TypedValue resolve(EvaluationContext context) {
        return TypedValue.string(value);
    }

    @Override
    public ValueType type() {
        return ValueType.STRING;
    }

    @Override
    public String display() {
        return "\"" + value + "\"";
    }
}
