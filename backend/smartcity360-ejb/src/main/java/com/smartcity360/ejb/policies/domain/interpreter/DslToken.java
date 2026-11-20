package com.smartcity360.ejb.policies.domain.interpreter;

public record DslToken(Type type, String lexeme, Object literal, int position) {
    public enum Type {
        IDENTIFIER, NUMBER, STRING, BOOLEAN,
        GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, EQUAL, NOT_EQUAL,
        AND, OR, NOT, LEFT_PAREN, RIGHT_PAREN, EOF
    }
}
