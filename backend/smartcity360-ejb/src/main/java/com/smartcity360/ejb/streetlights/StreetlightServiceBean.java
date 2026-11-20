package com.smartcity360.ejb.streetlights;

import com.smartcity360.common.streetlights.*;
import com.smartcity360.ejb.persistence.model.*;
import com.smartcity360.ejb.persistence.repository.*;
import com.smartcity360.ejb.security.SecurityAuditService;
import com.smartcity360.ejb.streetlights.domain.flyweight.*;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.*;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Stateless
@DeclareRoles({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT"})
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class StreetlightServiceBean implements StreetlightService {
    private static final Set<String> READ_ROLES = Set.of("ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF");

    @Inject
    LightingProfileRepository profiles;
    @Inject
    StreetlightRepository streetlights;
    @Inject
    ZoneRepository zones;
    @Inject
    UserRepository users;
    @Inject
    UserRoleRepository userRoles;
    @Inject
    SecurityAuditService securityAudit;
    @Inject
    LightingProfileFactory profileFactory;
    @Resource
    SessionContext session;
    @Resource
    TransactionSynchronizationRegistry transactions;

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public LightingProfileView createProfile(LightingProfileCreateCommand command) {
        UserEntity actor = actor();
        requireRole(actor, "ADMIN");
        LightingProfileEntity profile = newProfile(command, 1);
        profiles.lockProfileCode(profile.getProfileCode());
        if (profiles.findByProfileCode(profile.getProfileCode()).isPresent()) throw error("PROFILE_CODE_EXISTS");
        profiles.persist(profile);
        audit("LIGHTING_PROFILE_CREATED", actor, "LIGHTING_PROFILE", profile.getId(), profile.getProfileCode());
        return profileView(profile);
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public LightingProfileView createProfileVersion(UUID sourceProfileId, LightingProfileCreateCommand command) {
        UserEntity actor = actor();
        requireRole(actor, "ADMIN");
        LightingProfileEntity source = profiles.findByIdForUpdate(requiredId(sourceProfileId, "PROFILE_NOT_FOUND"))
                .orElseThrow(() -> error("PROFILE_NOT_FOUND"));
        if (!source.isActive()) throw error("PROFILE_INACTIVE");
        if (source.getProfileVersion() == Integer.MAX_VALUE) throw error("PROFILE_VERSION_LIMIT");
        LightingProfileEntity version = newProfile(command, source.getProfileVersion() + 1);
        profiles.lockProfileCode(version.getProfileCode());
        if (profiles.findByProfileCode(version.getProfileCode()).isPresent()) throw error("PROFILE_CODE_EXISTS");
        profiles.persist(version);
        audit("LIGHTING_PROFILE_VERSION_CREATED", actor, "LIGHTING_PROFILE", version.getId(),
                "source=" + source.getId());
        return profileView(version);
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public LightingProfileView deactivateProfile(UUID id) {
        UserEntity actor = actor();
        requireRole(actor, "ADMIN");
        LightingProfileEntity profile = profiles.findByIdForUpdate(requiredId(id, "PROFILE_NOT_FOUND"))
                .orElseThrow(() -> error("PROFILE_NOT_FOUND"));
        if (!profile.isActive()) return profileView(profile);
        if (streetlights.countByProfile(profile.getId()) > 0) throw error("PROFILE_IN_USE");
        profile.setActive(false);
        invalidateAfterCommit(profile.getId());
        audit("LIGHTING_PROFILE_DEACTIVATED", actor, "LIGHTING_PROFILE", profile.getId(), profile.getProfileCode());
        return profileView(profile);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public LightingProfileView getProfile(UUID id) {
        UserEntity actor = actor();
        requireAnyRole(actor, READ_ROLES);
        return profileView(requireProfile(id));
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<LightingProfileView> listProfiles(boolean activeOnly, int offset, int limit) {
        UserEntity actor = actor();
        requireAnyRole(actor, READ_ROLES);
        page(offset, limit);
        return profiles.listOrdered(activeOnly, offset, limit).stream().map(StreetlightServiceBean::profileView).toList();
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public StreetlightView register(StreetlightRegisterCommand command) {
        UserEntity actor = actor();
        requireRole(actor, "ADMIN");
        if (command == null) throw error("REQUEST_INVALID");
        String assetTag = text(command.assetTag(), 50, "ASSET_TAG_INVALID");
        streetlights.lockAssetTag(assetTag);
        if (streetlights.findByAssetTag(assetTag).isPresent()) throw error("ASSET_TAG_EXISTS");
        LightingProfileEntity profile = requireActiveProfileForUpdate(command.profileId());
        ZoneEntity zone = requireStreetZone(command.streetZoneId());
        StreetlightEntity light = new StreetlightEntity();
        light.setId(UUID.randomUUID());
        light.setAssetTag(assetTag);
        light.setProfile(profile);
        light.setStreetZone(zone);
        light.setLatitude(command.latitude());
        light.setLongitude(command.longitude());
        light.setAddressLabel(optionalText(command.addressLabel(), 255, "ADDRESS_INVALID"));
        light.setInstalledOn(command.installedOn());
        light.setOperationalState(StreetlightEntity.OperationalState.OFF);
        light.setRegisteredAt(OffsetDateTime.now());
        context(light);
        streetlights.persist(light);
        shared(profile);
        audit("STREETLIGHT_REGISTERED", actor, "STREETLIGHT", light.getId(), assetTag);
        return streetlightView(light);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public StreetlightView get(UUID id) {
        UserEntity actor = actor();
        requireAnyRole(actor, READ_ROLES);
        return streetlightView(requireStreetlight(id));
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<StreetlightView> list(UUID streetZoneId, int offset, int limit) {
        UserEntity actor = actor();
        requireAnyRole(actor, READ_ROLES);
        page(offset, limit);
        if (streetZoneId != null) requireStreetZone(streetZoneId);
        return streetlights.listJoined(streetZoneId, offset, limit).stream().map(this::streetlightView).toList();
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public LightingProfileView effectiveProfile(UUID streetlightId) {
        UserEntity actor = actor();
        requireAnyRole(actor, READ_ROLES);
        StreetlightEntity light = requireStreetlight(streetlightId);
        shared(light.getProfile());
        profileFactory.recordContextProcessed();
        return profileView(light.getProfile());
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public LightOperationView turnOn(UUID id) {
        return operate(id, LightCommand.on(), "STREETLIGHT_TURNED_ON");
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public LightOperationView turnOff(UUID id) {
        return operate(id, LightCommand.off(), "STREETLIGHT_TURNED_OFF");
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public LightOperationView setBrightness(UUID id, BrightnessCommand command) {
        if (command == null || command.brightnessPct() == null) throw error("BRIGHTNESS_INVALID");
        LightCommand lightCommand;
        try {
            lightCommand = LightCommand.brightness(command.brightnessPct());
        } catch (IllegalArgumentException exception) {
            throw error("BRIGHTNESS_INVALID");
        }
        return operate(id, lightCommand, "STREETLIGHT_BRIGHTNESS_CHANGED");
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public StreetlightView reassignProfile(UUID id, StreetlightProfileCommand command) {
        UserEntity actor = actor();
        requireRole(actor, "ADMIN");
        if (command == null) throw error("REQUEST_INVALID");
        StreetlightEntity light = lockStreetlight(id);
        LightingProfileEntity replacement = requireActiveProfileForUpdate(command.profileId());
        if (light.getProfile().getId().equals(replacement.getId())) return streetlightView(light);
        light.setProfile(replacement);
        shared(replacement);
        audit("STREETLIGHT_PROFILE_REASSIGNED", actor, "STREETLIGHT", light.getId(),
                "profile=" + replacement.getId());
        return streetlightView(light);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public StreetlightStatsView statistics() {
        UserEntity actor = actor();
        requireAnyRole(actor, READ_ROLES);
        FlyweightStatistics stats = profileFactory.statistics();
        return new StreetlightStatsView(streetlights.countAll(), streetlights.countDistinctActiveProfiles(),
                stats.cacheSize(), stats.capacity(), stats.hits(), stats.misses(), stats.evictions(),
                stats.contextsProcessed(), stats.contextToFlyweightRatio());
    }

    private LightOperationView operate(UUID id, LightCommand command, String auditAction) {
        UserEntity actor = actor();
        requireAnyRole(actor, Set.of("ADMIN", "CITY_MANAGER"));
        StreetlightEntity light = lockStreetlight(id);
        LightingProfileEntity profile = light.getProfile();
        if (!profile.isActive()) throw error("PROFILE_INACTIVE");
        SharedLightingProfile flyweight = shared(profile);
        StreetlightContext before = context(light);
        LightOperationResult result;
        try {
            result = flyweight.apply(before, command);
        } catch (FlyweightDomainException exception) {
            throw error(exception.code());
        }
        profileFactory.recordContextProcessed();
        if (result.changed()) {
            light.setOperationalState(StreetlightEntity.OperationalState.valueOf(result.currentState().name()));
            light.setBrightnessOverridePct(result.context().brightnessOverridePct() == null
                    ? null : result.context().brightnessOverridePct().shortValue());
            light.setLastSeenAt(OffsetDateTime.now());
            audit(auditAction, actor, "STREETLIGHT", light.getId(), "command=" + command.type());
        }
        return operationView(light.getId(), result);
    }

    private LightingProfileEntity newProfile(LightingProfileCreateCommand command, int version) {
        if (command == null) throw error("REQUEST_INVALID");
        LightingProfileEntity profile = new LightingProfileEntity();
        profile.setId(UUID.randomUUID());
        profile.setProfileCode(text(command.profileCode(), 50, "PROFILE_CODE_INVALID"));
        profile.setManufacturer(text(command.manufacturer(), 80, "MANUFACTURER_INVALID"));
        profile.setModel(text(command.model(), 100, "MODEL_INVALID"));
        profile.setWattage(wattage(command.wattage()));
        profile.setDefaultBrightnessPct((short) percentage(command.defaultBrightnessPct(), "BRIGHTNESS_INVALID"));
        profile.setControllerType(text(command.controllerType(), 40, "CONTROLLER_TYPE_INVALID"));
        profile.setFirmwareFamily(optionalText(command.firmwareFamily(), 50, "FIRMWARE_FAMILY_INVALID"));
        profile.setConfigJson(configuration(command.configurationJson()));
        profile.setProfileVersion(version);
        profile.setActive(true);
        profile.setCreatedAt(OffsetDateTime.now());
        // Construction validates the immutable snapshot before persistence.
        snapshot(profile);
        return profile;
    }

    private SharedLightingProfile shared(LightingProfileEntity profile) {
        ProfileKey key = new ProfileKey(profile.getId(), profile.getProfileVersion());
        return profileFactory.resolve(key, () -> snapshot(profile));
    }

    private static SharedLightingProfile snapshot(LightingProfileEntity profile) {
        return new SharedLightingProfile(new ProfileKey(profile.getId(), profile.getProfileVersion()),
                profile.getManufacturer(), profile.getModel(), profile.getWattage(),
                profile.getDefaultBrightnessPct(), profile.getControllerType(), profile.getFirmwareFamily(),
                profile.getConfigJson());
    }

    private StreetlightView streetlightView(StreetlightEntity light) {
        SharedLightingProfile flyweight = shared(light.getProfile());
        StreetlightContext context = context(light);
        profileFactory.recordContextProcessed();
        return new StreetlightView(light.getId(), light.getAssetTag(), light.getProfile().getId(),
                light.getProfile().getProfileVersion(), light.getStreetZone().getId(), light.getLatitude(),
                light.getLongitude(), light.getAddressLabel(), light.getOperationalState().name(),
                light.getBrightnessOverridePct() == null ? null : light.getBrightnessOverridePct().intValue(),
                flyweight.effectiveBrightness(context), light.getInstalledOn(), light.getLastSeenAt(),
                light.getRegisteredAt());
    }

    private static LightingProfileView profileView(LightingProfileEntity profile) {
        return new LightingProfileView(profile.getId(), profile.getProfileCode(), profile.getProfileVersion(),
                profile.getManufacturer(), profile.getModel(), profile.getWattage(),
                profile.getDefaultBrightnessPct(), profile.getControllerType(), profile.getFirmwareFamily(),
                profile.getConfigJson(), profile.isActive(), profile.getCreatedAt());
    }

    private static LightOperationView operationView(UUID id, LightOperationResult result) {
        return new LightOperationView(id, result.command().name(), result.previousState().name(),
                result.currentState().name(), result.changed(), result.context().brightnessOverridePct(),
                result.effectiveBrightnessPct());
    }

    private static StreetlightContext context(StreetlightEntity light) {
        try {
            return new StreetlightContext(light.getId(), light.getStreetZone().getId(), light.getLatitude(),
                    light.getLongitude(), light.getAddressLabel(), LightState.valueOf(light.getOperationalState().name()),
                    light.getBrightnessOverridePct() == null ? null : light.getBrightnessOverridePct().intValue());
        } catch (IllegalArgumentException exception) {
            throw error("STREETLIGHT_STATE_INVALID");
        }
    }

    private UserEntity actor() {
        String principal = session.getCallerPrincipal().getName();
        UserEntity user = users.findByEmail(principal).orElseThrow(() -> error("CALLER_NOT_FOUND"));
        if (user.getStatus() != UserEntity.Status.ACTIVE) throw error("CALLER_INACTIVE");
        return user;
    }

    private StreetlightEntity lockStreetlight(UUID id) {
        return streetlights.findByIdForUpdate(requiredId(id, "STREETLIGHT_NOT_FOUND"))
                .orElseThrow(() -> error("STREETLIGHT_NOT_FOUND"));
    }

    private StreetlightEntity requireStreetlight(UUID id) {
        return streetlights.findById(requiredId(id, "STREETLIGHT_NOT_FOUND"))
                .orElseThrow(() -> error("STREETLIGHT_NOT_FOUND"));
    }

    private LightingProfileEntity requireProfile(UUID id) {
        return profiles.findById(requiredId(id, "PROFILE_NOT_FOUND")).orElseThrow(() -> error("PROFILE_NOT_FOUND"));
    }

    private LightingProfileEntity requireActiveProfileForUpdate(UUID id) {
        LightingProfileEntity profile = profiles.findByIdForUpdate(requiredId(id, "PROFILE_NOT_FOUND"))
                .orElseThrow(() -> error("PROFILE_NOT_FOUND"));
        if (!profile.isActive()) throw error("PROFILE_INACTIVE");
        return profile;
    }

    private ZoneEntity requireStreetZone(UUID id) {
        ZoneEntity zone = zones.findById(requiredId(id, "ZONE_NOT_FOUND")).orElseThrow(() -> error("ZONE_NOT_FOUND"));
        if (zone.getZoneType() != ZoneEntity.ZoneType.STREET) throw error("ZONE_NOT_STREET");
        if (zone.getStatus() != ZoneEntity.Status.ACTIVE) throw error("ZONE_INACTIVE");
        return zone;
    }

    private void requireRole(UserEntity user, String role) {
        if (!userRoles.hasRole(user.getId(), role)) throw error("FORBIDDEN");
    }

    private void requireAnyRole(UserEntity user, Set<String> roles) {
        if (roles.stream().noneMatch(role -> userRoles.hasRole(user.getId(), role))) throw error("FORBIDDEN");
    }

    private void audit(String action, UserEntity actor, String entityType, UUID entityId, String detail) {
        securityAudit.recordApplicationEvent(action, actor.getId(), entityType, entityId,
                Json.createObjectBuilder().add("status", "SUCCESS")
                        .add("detail", detail == null ? "" : detail).build().toString());
    }

    private void invalidateAfterCommit(UUID profileId) {
        transactions.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) profileFactory.invalidateProfile(profileId);
            }
        });
    }

    private static String configuration(String value) {
        String candidate = value == null || value.isBlank() ? "{}" : value;
        try (var reader = Json.createReader(new StringReader(candidate))) {
            return reader.readObject().toString();
        } catch (JsonException | IllegalStateException exception) {
            throw error("CONFIGURATION_INVALID");
        }
    }

    private static BigDecimal wattage(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.precision() - value.scale() > 6 || value.scale() > 2)
            throw error("WATTAGE_INVALID");
        return value;
    }

    private static int percentage(Integer value, String code) {
        if (value == null || value < 0 || value > 100) throw error(code);
        return value;
    }

    private static String text(String value, int max, String code) {
        if (value == null || value.isBlank()) throw error(code);
        String stripped = value.strip();
        if (stripped.length() > max) throw error(code);
        return stripped;
    }

    private static String optionalText(String value, int max, String code) {
        if (value == null || value.isBlank()) return null;
        String stripped = value.strip();
        if (stripped.length() > max) throw error(code);
        return stripped;
    }

    private static UUID requiredId(UUID id, String code) {
        if (id == null) throw error(code);
        return id;
    }

    private static void page(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) throw error("PAGINATION_INVALID");
    }

    private static StreetlightApplicationException error(String code) {
        return new StreetlightApplicationException(code);
    }
}
