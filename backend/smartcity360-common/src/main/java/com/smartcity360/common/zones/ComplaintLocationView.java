package com.smartcity360.common.zones;

import java.util.UUID;

public record ComplaintLocationView(
        UUID id,
        String name,
        String code,
        String type) {
}
