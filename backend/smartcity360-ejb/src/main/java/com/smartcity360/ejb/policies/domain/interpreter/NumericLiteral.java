package com.smartcity360.ejb.policies.domain.interpreter;

import java.math.BigDecimal;
import java.util.Objects;

public record NumericLiteral(BigDecimal value) implements Operand {
    public NumericLiteral {
        Objects.requireNonNull(value);
    }

    @Override
    public TypedValue resolve(EvaluationContext context) {
        return TypedValue.numeric(value);
    }

    @Override
    public ValueType type() {
        return ValueType.NUMERIC;
    }

    @Override
    public String display() {
        return value.toPlainString();
    }
}
