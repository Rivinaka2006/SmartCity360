package com.smartcity360.ejb.policies.domain.interpreter;

import java.math.BigDecimal;
import java.util.*;

public final class PolicyVariableRegistry {
    private final Map<String, PolicyVariable> variables;

    public PolicyVariableRegistry(Collection<PolicyVariable> variables) {
        Map<String, PolicyVariable> copy = new LinkedHashMap<>();
        for (PolicyVariable variable : variables) {
            String name = normalize(variable.name());
            if (copy.putIfAbsent(name, variable) != null)
                throw new IllegalArgumentException("Duplicate variable " + name);
        }
        this.variables = Map.copyOf(copy);
    }

    public static PolicyVariableRegistry standard() {
        Set<PolicyDomain> all = EnumSet.allOf(PolicyDomain.class);
        return new PolicyVariableRegistry(List.of(
                new PolicyVariable("NOISE_DB", ValueType.NUMERIC, Set.of(PolicyDomain.NOISE, PolicyDomain.GENERAL),
                        BigDecimal.ZERO, new BigDecimal("200"), false, 0),
                new PolicyVariable("HOUR", ValueType.NUMERIC, all,
                        BigDecimal.ZERO, new BigDecimal("23"), true, 0),
                new PolicyVariable("PARKING_MINUTES", ValueType.NUMERIC,
                        Set.of(PolicyDomain.PARKING, PolicyDomain.GENERAL), BigDecimal.ZERO,
                        new BigDecimal("525600"), false, 0),
                new PolicyVariable("ZONE", ValueType.STRING, all, null, null, false, 100),
                new PolicyVariable("IS_WEEKEND", ValueType.BOOLEAN, all, null, null, false, 0)
        ));
    }

    public PolicyVariable require(String rawName, PolicyDomain domain, int position) {
        String name = normalize(rawName);
        PolicyVariable variable = variables.get(name);
        if (variable == null) throw error("UNKNOWN_VARIABLE", "Unknown policy variable " + name, position);
        if (!variable.domains().contains(domain)) throw error("VARIABLE_NOT_ALLOWED_FOR_DOMAIN",
                name + " is not available for " + domain + " policies", position);
        return variable;
    }

    public Optional<PolicyVariable> find(String rawName) {
        return Optional.ofNullable(variables.get(normalize(rawName)));
    }

    static String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private static DslValidationException error(String code, String message, int position) {
        return new DslValidationException(code, message, position);
    }
}
