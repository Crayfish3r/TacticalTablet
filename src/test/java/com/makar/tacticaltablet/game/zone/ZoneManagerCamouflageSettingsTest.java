package com.makar.tacticaltablet.game.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZoneManagerCamouflageSettingsTest {
    @Test
    void legacyMapHasCamouflageDisabled() {
        var result = parse("{\"zoneCenterX\":1,\"zoneCenterZ\":2,\"zoneRandomRadius\":3}");
        assertTrue(result.preset().isEmpty());
        assertFalse(result.unknown());
    }

    @Test
    void knownPresetIsEnabledAndNoneExplicitlyDisables() {
        assertEquals("spruce", parse("{\"camouflagePreset\":\"spruce\"}").preset().orElseThrow());
        assertTrue(parse("{\"camouflagePreset\":\"none\"}").preset().isEmpty());
        assertTrue(parse("{\"camouflagePreset\":\"  \"}").preset().isEmpty());
    }

    @Test
    void unknownPresetDisablesWithoutThrowing() {
        var result = parse("{\"camouflagePreset\":\"spuuuce\"}");
        assertTrue(result.preset().isEmpty());
        assertTrue(result.unknown());
        assertEquals("spuuuce", result.unknownPreset());
    }

    private static com.makar.tacticaltablet.camouflage.CamouflageMapSelection.Result parse(String json) {
        return ZoneManager.parseCamouflagePresetJson(json, "spruce"::equals);
    }
}
