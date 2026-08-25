package com.makar.tacticaltablet.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/** Central owner for the intentionally global rules required by the match server. */
public final class MatchGameRules {
    private MatchGameRules() {
    }

    public static void apply(MinecraftServer server) {
        if (server == null) return;
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, server);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(false, server);
        rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(false, server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(true, server);
    }
}
