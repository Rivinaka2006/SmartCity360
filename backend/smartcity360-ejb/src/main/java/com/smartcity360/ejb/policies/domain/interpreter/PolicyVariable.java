package com.smartcity360.ejb.policies.domain.interpreter;

import java.math.BigDecimal;
import java.util.Set;

public record PolicyVariable(String name, ValueType type, Set<PolicyDomain> domains,
                             BigDecimal minimum, BigDecimal maximum, boolean integerOnly, int maxTextLength) {
    public PolicyVariable {
        domains = Set.copyOf(domains);
    }
}
