package com.smartcity360.common.energy;
public class EnergyStreamServiceException extends Exception { private final String code; public EnergyStreamServiceException(String code){super(code);this.code=code;}public String code(){return code;} }
