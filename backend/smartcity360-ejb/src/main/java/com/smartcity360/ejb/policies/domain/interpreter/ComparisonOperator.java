package com.smartcity360.ejb.policies.domain.interpreter;

public enum ComparisonOperator {
    GREATER(">"), GREATER_EQUAL(">="), LESS("<"), LESS_EQUAL("<="), EQUAL("=="), NOT_EQUAL("!=");
    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
