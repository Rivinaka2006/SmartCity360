package com.smartcity360.common.zones;

import java.util.List;
import java.util.UUID;

public record ZoneTreeView(UUID id, String type, String code, String name, List<ZoneTreeView> children) {}
