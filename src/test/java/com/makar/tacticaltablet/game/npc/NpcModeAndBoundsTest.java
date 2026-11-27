package com.makar.tacticaltablet.game.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcModeAndBoundsTest {
    @Test void defaultClanWarSpecialAndUnknownModesAreClosedButStandardTeamModesWork() {
        var c = new NpcEncounterConfig();
        for (String mode : new String[]{"SOLO", "DUO", "TRIO", "SQUADS"}) {
            assertTrue(NpcModePolicy.allowed(c, false, false, mode));
            assertFalse(NpcModePolicy.allowed(c, false, true, mode));
            assertFalse(NpcModePolicy.allowed(c, true, false, mode));
        }
        assertFalse(NpcModePolicy.allowed(c, false, false, "NEW_SPECIAL"));
        assertFalse(NpcModePolicy.allowed(c, false, false, null));
        c.allowSpecialModes = true;
        assertTrue(NpcModePolicy.allowed(c, false, true, "SOLO"));
        assertFalse(NpcModePolicy.allowed(c, true, true, "SQUADS"));
        c.allowClanWar = true; assertTrue(NpcModePolicy.allowed(c, true, true, "SQUADS"));
    }

    @Test void groupEnvelopeMustFitEveryBorderEdgeAndRejectSmallOrInvalidZones() {
        assertTrue(NpcPlacementBounds.inside(0, 0, -90, 90, -90, 90, 19));
        assertTrue(NpcPlacementBounds.inside(70.99, -70.99, -90, 90, -90, 90, 19));
        assertFalse(NpcPlacementBounds.inside(71, 0, -90, 90, -90, 90, 19));
        assertFalse(NpcPlacementBounds.inside(-71, 0, -90, 90, -90, 90, 19));
        assertFalse(NpcPlacementBounds.inside(0, 71, -90, 90, -90, 90, 19));
        assertFalse(NpcPlacementBounds.inside(0, -71, -90, 90, -90, 90, 19));
        assertFalse(NpcPlacementBounds.inside(0, 0, -18, 18, -18, 18, 19));
        assertFalse(NpcPlacementBounds.inside(Double.NaN, 0, -90, 90, -90, 90, 19));
        assertFalse(NpcPlacementBounds.inside(0, 0, -90, 90, -90, 90, -1));
    }
}
