package com.smartcity360.common.policies;

public class PolicyServiceException extends RuntimeException {
    private final String code;
    public PolicyServiceException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
