package com.smartcity360.common.streetlights;

import java.util.UUID;

public record LightOperationView(UUID streetlightId, String command, String previousState,
                                 String currentState, boolean changed,
                                 Integer brightnessOverridePct, int effectiveBrightnessPct) {}
