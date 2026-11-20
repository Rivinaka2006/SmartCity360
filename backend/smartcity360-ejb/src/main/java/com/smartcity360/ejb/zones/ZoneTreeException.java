package com.smartcity360.ejb.zones;

public final class ZoneTreeException extends RuntimeException {
    private final String code;

    public ZoneTreeException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
