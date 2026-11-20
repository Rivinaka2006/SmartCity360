package com.smartcity360.ejb.policies.domain.interpreter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class EvaluationContext {
    private final PolicyDomain domain;
    private final Map<String, TypedValue> values;

    private EvaluationContext(PolicyDomain domain, Map<String, TypedValue> values) {
        this.domain = domain;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static EvaluationContext create(PolicyDomain domain, Map<String, ?> rawValues,
                                           PolicyVariableRegistry registry, DslLimits limits) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(rawValues, "rawValues");
        if (rawValues.size() > limits.maxContextEntries())
            throw error("CONTEXT_ENTRY_LIMIT", "Too many context entries");
        if (payloadBytes(rawValues) > limits.maxContextPayloadBytes())
            throw error("CONTEXT_PAYLOAD_LIMIT", "Context payload is too large");
        Map<String, TypedValue> converted = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : rawValues.entrySet()) {
            String name = PolicyVariableRegistry.normalize(entry.getKey());
            PolicyVariable variable = registry.require(name, domain, -1);
            if (converted.putIfAbsent(name, convert(variable, entry.getValue())) != null)
                throw error("DUPLICATE_CONTEXT_VARIABLE", "Context variable " + name + " is supplied more than once");
        }
        return new EvaluationContext(domain, converted);
    }

    public TypedValue require(String name) {
        TypedValue value = values.get(PolicyVariableRegistry.normalize(name));
        if (value == null) throw error("MISSING_CONTEXT_VALUE", "Missing context value for " + name);
        return value;
    }

    public PolicyDomain domain() {
        return domain;
    }

    public Map<String, TypedValue> values() {
        return values;
    }

    private static TypedValue convert(PolicyVariable variable, Object raw) {
        if (raw == null) throw error("CONTEXT_TYPE_MISMATCH", variable.name() + " may not be null");
        return switch (variable.type()) {
            case NUMERIC -> numeric(variable, raw);
            case STRING -> {
                if (!(raw instanceof String value))
                    throw error("CONTEXT_TYPE_MISMATCH", variable.name() + " must be a string");
                if (value.length() > variable.maxTextLength())
                    throw error("CONTEXT_VALUE_OUT_OF_RANGE", variable.name() + " is too long");
                yield TypedValue.string(value);
            }
            case BOOLEAN -> {
                if (!(raw instanceof Boolean value))
                    throw error("CONTEXT_TYPE_MISMATCH", variable.name() + " must be boolean");
                yield TypedValue.bool(value);
            }
        };
    }

    private static TypedValue numeric(PolicyVariable variable, Object raw) {
        BigDecimal value;
        try {
            if (raw instanceof BigDecimal decimal) value = decimal;
            else if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long)
                value = BigDecimal.valueOf(((Number) raw).longValue());
            else if (raw instanceof Number number) value = new BigDecimal(number.toString());
            else throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw error("CONTEXT_TYPE_MISMATCH", variable.name() + " must be a finite decimal number");
        }
        if (variable.integerOnly() && value.stripTrailingZeros().scale() > 0)
            throw error("CONTEXT_VALUE_OUT_OF_RANGE", variable.name() + " must be an integer");
        if ((variable.minimum() != null && value.compareTo(variable.minimum()) < 0)
                || (variable.maximum() != null && value.compareTo(variable.maximum()) > 0))
            throw error("CONTEXT_VALUE_OUT_OF_RANGE", variable.name() + " is outside its permitted range");
        return TypedValue.numeric(value);
    }

    private static int payloadBytes(Map<String, ?> values) {
        int bytes = 2;
        for (Map.Entry<String, ?> entry : values.entrySet())
            bytes += String.valueOf(entry.getKey()).getBytes(StandardCharsets.UTF_8).length
                    + String.valueOf(entry.getValue()).getBytes(StandardCharsets.UTF_8).length + 6;
        return bytes;
    }

    private static DslValidationException error(String code, String message) {
        return new DslValidationException(code, message, -1);
    }
}
