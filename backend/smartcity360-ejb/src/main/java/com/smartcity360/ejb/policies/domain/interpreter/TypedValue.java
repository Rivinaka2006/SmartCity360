package com.smartcity360.ejb.policies.domain.interpreter;

import java.math.BigDecimal;
import java.util.Objects;

public record TypedValue(ValueType type, Object value) {
    public TypedValue {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }

    public static TypedValue numeric(BigDecimal value) {
        return new TypedValue(ValueType.NUMERIC, value);
    }

    public static TypedValue string(String value) {
        return new TypedValue(ValueType.STRING, value);
    }

    public static TypedValue bool(boolean value) {
        return new TypedValue(ValueType.BOOLEAN, value);
    }

    public BigDecimal number() {
        return (BigDecimal) value;
    }
}
