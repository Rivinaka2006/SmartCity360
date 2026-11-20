package com.smartcity360.common.streetlights;

import java.math.BigDecimal;

public record LightingProfileCreateCommand(String profileCode, String manufacturer, String model,
                                           BigDecimal wattage, Integer defaultBrightnessPct,
                                           String controllerType, String firmwareFamily,
                                           String configurationJson) {}
