package com.smartcity360.web.complaints;

import com.smartcity360.common.complaints.ComplaintView;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintViewJsonTest {
    @Test
    void serializesUnassignedUserExplicitlyAsNull() throws Exception {
        String json = serialize(null);

        assertTrue(json.contains("\"assignedToUserId\":null"), json);
        assertTrue(json.contains("\"resolvedAt\":null"), json);
        assertTrue(json.contains("\"zoneName\":\"Gannoruwa\""), json);
        assertTrue(json.contains("\"zoneCode\":\"S001\""), json);
        assertTrue(json.contains("\"assignedUserDisplayName\":null"), json);
    }

    @Test
    void serializesAssignedUserIdentifier() throws Exception {
        UUID assignedUserId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        String json = serialize(assignedUserId);

        assertTrue(json.contains("\"assignedToUserId\":\"" + assignedUserId + "\""), json);
        assertTrue(json.contains("\"assignedUserDisplayName\":\"Local Staff User\""), json);
    }

    private static String serialize(UUID assignedUserId) throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-07T00:00:00Z");
        ComplaintView view = new ComplaintView(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "SC-001",
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "Streetlight inspection test",
                "Controlled test",
                "LOW",
                "OPEN",
                assignedUserId,
                "LOCAL_STAFF",
                now,
                now,
                null,
                "Gannoruwa",
                "S001",
                assignedUserId == null ? null : "Local Staff User");
        try (Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.toJson(view);
        }
    }
}
