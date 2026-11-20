package com.smartcity360.ejb.zones;

public final class ZoneOperationException extends RuntimeException {
    private final String code;

    public ZoneOperationException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
