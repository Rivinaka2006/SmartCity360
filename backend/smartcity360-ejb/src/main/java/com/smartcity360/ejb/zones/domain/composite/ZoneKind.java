package com.smartcity360.ejb.zones.domain.composite;

public enum ZoneKind {
    CITY, DISTRICT, STREET, BUILDING;

    public ZoneKind childKind() {
        return switch (this) {
            case CITY -> DISTRICT;
            case DISTRICT -> STREET;
            case STREET -> BUILDING;
            case BUILDING -> null;
        };
    }
}
