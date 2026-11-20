package com.smartcity360.common.zones;

public class ZoneServiceException extends RuntimeException {
    private final String code;

    public ZoneServiceException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
