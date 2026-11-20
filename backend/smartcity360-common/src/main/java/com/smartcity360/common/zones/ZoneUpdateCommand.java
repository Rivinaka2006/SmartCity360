package com.smartcity360.common.zones;

import java.util.UUID;

public record ZoneUpdateCommand(String code, String name, UUID parentId) {}
