package com.smartcity360.ejb.streetlights.domain.flyweight;

public final class FlyweightDomainException extends RuntimeException {
    private final String code;

    public FlyweightDomainException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
