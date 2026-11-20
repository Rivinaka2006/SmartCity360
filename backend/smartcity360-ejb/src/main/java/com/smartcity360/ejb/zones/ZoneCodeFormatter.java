package com.smartcity360.ejb.zones;

import com.smartcity360.ejb.persistence.model.ZoneEntity;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

final class ZoneCodeFormatter {
    private static final Pattern NUMERIC_CODE = Pattern.compile("\\d{1,39}");
    private static final Pattern PREFIXED_CODE = Pattern.compile("[CDSB]\\d{1,39}");
    private static final Map<ZoneEntity.ZoneType, String> PREFIXES = prefixes();

    private ZoneCodeFormatter() {
    }

    static String normalizeNew(ZoneEntity.ZoneType type, String value) {
        if (value == null) {
            throw invalidCode();
        }
        if (NUMERIC_CODE.matcher(value).matches()) {
            return PREFIXES.get(type) + value;
        }
        if (!PREFIXED_CODE.matcher(value).matches() || !value.startsWith(PREFIXES.get(type))) {
            throw invalidCode();
        }
        return value;
    }

    static String normalizeUpdate(ZoneEntity.ZoneType type, String value, String existingCode) {
        if (value != null && value.equals(existingCode) && NUMERIC_CODE.matcher(value).matches()) {
            return existingCode;
        }
        return normalizeNew(type, value);
    }

    private static ZoneApplicationException invalidCode() {
        return new ZoneApplicationException("ZONE_CODE_INVALID");
    }

    private static Map<ZoneEntity.ZoneType, String> prefixes() {
        Map<ZoneEntity.ZoneType, String> prefixes = new EnumMap<>(ZoneEntity.ZoneType.class);
        prefixes.put(ZoneEntity.ZoneType.CITY, "C");
        prefixes.put(ZoneEntity.ZoneType.DISTRICT, "D");
        prefixes.put(ZoneEntity.ZoneType.STREET, "S");
        prefixes.put(ZoneEntity.ZoneType.BUILDING, "B");
        return Map.copyOf(prefixes);
    }
}
