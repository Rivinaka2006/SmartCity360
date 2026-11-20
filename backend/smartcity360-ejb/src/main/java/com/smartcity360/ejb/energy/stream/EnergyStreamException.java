package com.smartcity360.ejb.energy.stream;

public class EnergyStreamException extends RuntimeException {
    private final String code;

    public EnergyStreamException(String code) {
        super(code);
        this.code = code;
    }

    public EnergyStreamException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
