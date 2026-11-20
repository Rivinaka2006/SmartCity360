package com.smartcity360.common.zones;

import java.util.UUID;

public record ZoneCreateCommand(String type, String code, String name, UUID parentId) {}
