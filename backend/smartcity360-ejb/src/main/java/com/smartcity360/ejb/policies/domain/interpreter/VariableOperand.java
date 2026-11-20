package com.smartcity360.ejb.policies.domain.interpreter;

import java.util.Objects;

public record VariableOperand(String name, ValueType type) implements Operand {
    public VariableOperand {
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
    }

    @Override
    public TypedValue resolve(EvaluationContext context) {
        return context.require(name);
    }

    @Override
    public String display() {
        return name;
    }
}
