package com.smartcity360.ejb.complaints.domain.chain;

public enum HandlerLevel {
    LOCAL_STAFF, SUPERVISOR, CITY_MANAGER;

    public HandlerLevel next() {
        return switch (this) {
            case LOCAL_STAFF -> SUPERVISOR;
            case SUPERVISOR -> CITY_MANAGER;
            case CITY_MANAGER -> null;
        };
    }
}
