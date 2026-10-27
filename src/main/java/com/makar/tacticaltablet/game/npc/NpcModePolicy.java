package com.makar.tacticaltablet.game.npc;

import java.util.Set;

final class NpcModePolicy {
    private static final Set<String> BATTLE_ROYALE_MODES = Set.of("SOLO", "DUO", "TRIO", "SQUADS");

    static boolean allowed(NpcEncounterConfig c, boolean clanWar, boolean special, String mode) {
        return allowed(c, clanWar, special, mode, false);
    }
    static boolean allowed(NpcEncounterConfig c, boolean clanWar, boolean special, String mode, boolean arena) {
        if (arena) return true;
        if (clanWar) return c.allowClanWar;
        return c.allowSpecialModes || (!special && BATTLE_ROYALE_MODES.contains(mode == null ? "" : mode));
    }
}
