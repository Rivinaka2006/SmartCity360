package com.smartcity360.common.streetlights;

public class StreetlightServiceException extends RuntimeException {
    private final String code;
    public StreetlightServiceException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
