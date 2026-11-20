package com.smartcity360.ejb.policies.domain.interpreter;

public final class DslValidationException extends RuntimeException {
    private final String code;
    private final int position;

    public DslValidationException(String code, String message, int position) {
        super(message);
        this.code = code;
        this.position = position;
    }

    public String code() {
        return code;
    }

    public int position() {
        return position;
    }
}
