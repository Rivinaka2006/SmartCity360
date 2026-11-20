package com.smartcity360.common.users;

public class UserManagementException extends RuntimeException {
    private final String code;

    public UserManagementException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
