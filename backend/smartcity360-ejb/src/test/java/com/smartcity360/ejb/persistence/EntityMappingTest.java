package com.smartcity360.ejb.persistence;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EntityMappingTest {

    @Test
    void mapsAllSeventeenCanonicalTables() throws Exception {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("UserEntity", "users");
        mappings.put("RoleEntity", "roles");
        mappings.put("UserRoleEntity", "user_roles");
        mappings.put("ZoneEntity", "zones");
        mappings.put("BuildingPowerStateEntity", "building_power_states");
        mappings.put("ZoneCommandEntity", "zone_commands");
        mappings.put("EnergyMeterEntity", "energy_meters");
        mappings.put("EnergyReadingEntity", "energy_readings");
        mappings.put("EnergyStreamRunEntity", "energy_stream_runs");
        mappings.put("ComplaintEntity", "complaints");
        mappings.put("ComplaintEventEntity", "complaint_events");
        mappings.put("LightingProfileEntity", "lighting_profiles");
        mappings.put("StreetlightEntity", "streetlights");
        mappings.put("PolicyEntity", "policies");
        mappings.put("PolicyVersionEntity", "policy_versions");
        mappings.put("PolicyEvaluationEntity", "policy_evaluations");
        mappings.put("AuditLogEntity", "audit_logs");

        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            Class<?> type = Class.forName("com.smartcity360.ejb.persistence.model." + mapping.getKey());
            assertNotNull(type.getAnnotation(Entity.class), mapping.getKey() + " must be an entity");
            assertEquals(mapping.getValue(), type.getAnnotation(Table.class).name());
        }
    }

    @Test
    void userRoleUsesEmbeddableCompositeKey() throws Exception {
        Class<?> key = Class.forName("com.smartcity360.ejb.persistence.model.UserRoleId");
        assertNotNull(key.getAnnotation(Embeddable.class));
        Class<?> entity = Class.forName("com.smartcity360.ejb.persistence.model.UserRoleEntity");
        assertNotNull(entity.getDeclaredField("id").getAnnotation(jakarta.persistence.EmbeddedId.class));
    }

    @Test
    void jsonbFieldsUseThePostgresqlConverter() throws Exception {
        Class<?> converter = Class.forName("com.smartcity360.ejb.persistence.converter.JsonbConverter");
        assertNotNull(converter.getAnnotation(jakarta.persistence.Converter.class));
        for (String field : new String[]{
                "AuditLogEntity.detailsJson", "EnergyStreamRunEntity.decoratorChain",
                "LightingProfileEntity.configJson", "PolicyEvaluationEntity.contextJson"}) {
            String[] parts = field.split("\\.");
            Class<?> entity = Class.forName("com.smartcity360.ejb.persistence.model." + parts[0]);
            jakarta.persistence.Convert annotation = entity.getDeclaredField(parts[1]).getAnnotation(jakarta.persistence.Convert.class);
            assertNotNull(annotation, field);
            assertEquals(converter, annotation.converter());
        }
    }

    @Test
    void energyMeterMapsNullableCredentialReferencePair() throws Exception {
        Class<?> entity = Class.forName("com.smartcity360.ejb.persistence.model.EnergyMeterEntity");
        jakarta.persistence.Column keyId = entity.getDeclaredField("credentialKeyId").getAnnotation(jakarta.persistence.Column.class);
        jakarta.persistence.Column version = entity.getDeclaredField("credentialKeyVersion").getAnnotation(jakarta.persistence.Column.class);
        assertEquals("credential_key_id", keyId.name());
        assertEquals(128, keyId.length());
        assertEquals("credential_key_version", version.name());
        assertEquals(String.class, entity.getDeclaredField("credentialKeyId").getType());
        assertEquals(Integer.class, entity.getDeclaredField("credentialKeyVersion").getType());
    }
}
