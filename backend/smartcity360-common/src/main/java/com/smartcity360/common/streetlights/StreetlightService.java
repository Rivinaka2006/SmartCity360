package com.smartcity360.common.streetlights;

import java.util.*;

public interface StreetlightService {
    LightingProfileView createProfile(LightingProfileCreateCommand command);
    LightingProfileView createProfileVersion(UUID sourceProfileId, LightingProfileCreateCommand command);
    LightingProfileView deactivateProfile(UUID id);
    LightingProfileView getProfile(UUID id);
    List<LightingProfileView> listProfiles(boolean activeOnly, int offset, int limit);
    StreetlightView register(StreetlightRegisterCommand command);
    StreetlightView get(UUID id);
    List<StreetlightView> list(UUID streetZoneId, int offset, int limit);
    LightingProfileView effectiveProfile(UUID streetlightId);
    LightOperationView turnOn(UUID id);
    LightOperationView turnOff(UUID id);
    LightOperationView setBrightness(UUID id, BrightnessCommand command);
    StreetlightView reassignProfile(UUID id, StreetlightProfileCommand command);
    StreetlightStatsView statistics();
}
