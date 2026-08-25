package com.makar.tacticaltablet.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Owns the scoreboard objectives that are part of the mod's match protocol. */
public final class MatchScoreboard {
    public static final String GAME_STATE_OBJECTIVE = "gameState";
    public static final String LIVES_OBJECTIVE = "lives";

    private MatchScoreboard() {
    }

    public static void ensureObjectives(MinecraftServer server) {
        if (server == null) return;
        getOrCreateDummy(server.getScoreboard(), GAME_STATE_OBJECTIVE);
        getOrCreateDummy(server.getScoreboard(), LIVES_OBJECTIVE);
    }

    public static Objective getOrCreateDummy(Scoreboard scoreboard, String name) {
        if (scoreboard == null || name == null || name.isBlank()) return null;
        Objective existing = scoreboard.getObjective(name);
        if (existing != null) return existing;
        return scoreboard.addObjective(
                name,
                ObjectiveCriteria.DUMMY,
                Component.literal(name),
                ObjectiveCriteria.RenderType.INTEGER
        );
    }
}
