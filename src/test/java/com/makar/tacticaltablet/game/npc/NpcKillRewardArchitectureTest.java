package com.makar.tacticaltablet.game.npc;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class NpcKillRewardArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test void optionalTypesStayBehindAdapterAndDeathCannotEnterPvpOrPersistencePaths() throws Exception {
        String handler = Files.readString(MAIN.resolve("game/npc/NpcKillRewardHandler.java"));
        assertTrue(handler.contains("priority = EventPriority.LOWEST, receiveCanceled = true"));
        assertTrue(handler.contains("event.isCanceled()"));
        assertTrue(handler.contains("ResponsiblePlayerResolver.resolve(source)"));
        assertTrue(handler.contains("projectile.getOwner() == killer"));
        assertTrue(handler.indexOf("claimDeath(") < handler.indexOf("award(killer)"));
        for (String forbidden : new String[]{"dev.zuma.", "addKill(", "recordMatchKill(", "handleDeath(",
                "checkForMatchEnd(", "savePlayer(", "saveOffline(", "findFresh(", "addXPDeferredSync("}) {
            assertFalse(handler.contains(forbidden), forbidden);
        }
        assertTrue(handler.contains("addCoins(killer, NPC_KILL_COINS)"));
        assertTrue(handler.contains("getCoins(killer) - before"));
        assertTrue(handler.contains("PlayerTabletState.getSelectedClass(killer)"));
        assertEquals(1, handler.split("ClassXPManager.sync\\(killer\\)", -1).length - 1);
    }

    @Test void baseXpUsesExistingRulesAndClaimRegistryClosesWithSession() throws Exception {
        String xp = Files.readString(MAIN.resolve("progression/ClassXPManager.java"));
        String base = xp.substring(xp.indexOf("public static int addBaseXPDeferredSync"),
                xp.indexOf("public static void addXPToAllClasses"));
        assertTrue(base.contains("MapSetManager.isChaosSet()"));
        assertTrue(base.contains("isShopClass(clazz)"));
        assertTrue(base.contains("PlayerProgressManager.addXP(player, clazz, amount)"));
        assertFalse(base.contains("applyBoost"));
        String manager = Files.readString(MAIN.resolve("game/npc/NpcEncounterManager.java"));
        assertTrue(manager.contains("m.schedule.owns(victim.getUUID())"));
        assertTrue(manager.contains("m.schedule.matchId().equals(identity.ownerMatchId())"));
        assertTrue(manager.contains("m.deathClaims.clear()"));
        assertFalse(Files.readString(MAIN.resolve("game/npc/NpcDeathClaims.java")).contains("static final Set"));
        String feed = Files.readString(MAIN.resolve("game/TacticalKillFeed.java"));
        String npcFeed = feed.substring(feed.indexOf("public static void publishNpcKill"),
                feed.indexOf("static KillFeedPacket.Cause classifyCause(String"));
        assertTrue(npcFeed.contains("isMatchViewer(viewer, null)"));
        assertFalse(npcFeed.contains("sendToAll"));
    }
}
