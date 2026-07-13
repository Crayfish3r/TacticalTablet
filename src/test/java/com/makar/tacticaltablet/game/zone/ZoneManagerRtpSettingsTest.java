package com.makar.tacticaltablet.game.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneManagerRtpSettingsTest {

    @Test
    void legacyConfigKeepsWorldBorderSurfaceMode() {
        ZoneManager.RtpSettings settings = ZoneManager.parseRtpSettingsJson("""
                { "zoneCenterX": 0.0, "zoneCenterZ": 0.0, "zoneRandomRadius": 0 }
                """);

        assertEquals(ZoneManager.RtpPlacementMode.WORLD_BORDER_SURFACE, settings.mode());
        assertTrue(settings.valid());
        assertEquals(3, settings.requiredSolidBlocksBelow());
    }

    @Test
    void fixedYBoxLoadsEveryConfiguredField() {
        ZoneManager.RtpSettings settings = ZoneManager.parseRtpSettingsJson("""
                { "rtp": {
                  "mode": "FIXED_Y_BOX", "minX": -100, "maxX": 130,
                  "minZ": -145, "maxZ": 85, "spawnY": 42, "maxAttempts": 500,
                  "localSearchRadius": 10, "teamSpreadRadius": 4,
                  "requiredSolidBlocksBelow": 1, "requireInsideWorldBorder": false
                }}
                """);

        assertEquals(ZoneManager.RtpPlacementMode.FIXED_Y_BOX, settings.mode());
        assertEquals(-100, settings.minX());
        assertEquals(130, settings.maxX());
        assertEquals(-145, settings.minZ());
        assertEquals(85, settings.maxZ());
        assertEquals(42, settings.spawnY());
        assertEquals(500, settings.maxAttempts());
        assertEquals(10, settings.localSearchRadius());
        assertEquals(4, settings.teamSpreadRadius());
        assertEquals(1, settings.requiredSolidBlocksBelow());
        assertFalse(settings.requireInsideWorldBorder());
        assertTrue(settings.valid());
    }

    @Test
    void fixedYBoxUsesExistingSafetyDefaultsForOptionalFields() {
        ZoneManager.RtpSettings settings = ZoneManager.parseRtpSettingsJson("""
                { "rtp": {
                  "mode": "FIXED_Y_BOX", "minX": 0, "maxX": 1,
                  "minZ": 0, "maxZ": 1, "spawnY": 42
                }}
                """);

        assertEquals(80, settings.maxAttempts());
        assertEquals(14, settings.localSearchRadius());
        assertEquals(4, settings.teamSpreadRadius());
        assertEquals(3, settings.requiredSolidBlocksBelow());
        assertTrue(settings.requireInsideWorldBorder());
    }

    @Test
    void unknownModeIsInvalidAndNeverBecomesSurfaceMode() {
        ZoneManager.RtpSettings settings = ZoneManager.parseRtpSettingsJson("""
                { "rtp": { "mode": "ROOF_FALLBACK" } }
                """);

        assertEquals(ZoneManager.RtpPlacementMode.FIXED_Y_BOX, settings.mode());
        assertFalse(settings.valid());
        assertTrue(settings.validationError().contains("unknown mode"));
    }
}
