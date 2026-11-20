package com.smartcity360.common.streetlights;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LightingProfileView(UUID id, String profileCode, int profileVersion,
                                  String manufacturer, String model, BigDecimal wattage,
                                  int defaultBrightnessPct, String controllerType,
                                  String firmwareFamily, String configurationJson,
                                  boolean active, OffsetDateTime createdAt) {}
