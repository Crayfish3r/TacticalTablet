package com.makar.tacticaltablet.game;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class SingleplayerArchitectureTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/makar/tacticaltablet", path));
    }
    @Test void localWorldSelectionUsesVanillaScreenAndCannotReplaceLoadedWorld() throws Exception {
        String screen = source("client/gui/JoinServerConfigScreen.java");
        assertTrue(screen.contains("SelectWorldScreen(this)"));
        assertTrue(screen.contains("Component.translatable(\"menu.singleplayer\")"));
        assertTrue(screen.contains("minecraft.level == null"));
        assertTrue(source("client/event/ClientScreenEvents.java").contains("hasSingleplayerServer()) return"));
    }
    @Test void localLifecycleKeepsPersistenceButSkipsRulesAndMatchTick() throws Exception {
        String events = source("game/ServerEvents.java");
        assertTrue(events.contains("PlayerProgressManager.onServerStarted(event.getServer());\n        if (!ServerRules.enabled(event.getServer())) return;"));
        assertTrue(events.contains("PlayerProgressManager.tick(event.getServer());\n        if (!ServerRules.enabled(event.getServer())) return;"));
        assertTrue(events.contains("PlayerProgressManager.loadPlayer(local)"));
        assertTrue(events.contains("PlayerProgressManager.saveAndUnloadPlayer(local)"));
        assertTrue(source("game/ServerRules.java").contains("server.isDedicatedServer()"));
        assertFalse(ServerRules.enabled(null));
        assertTrue(source("game/MatchGameRules.java").contains("if (!ServerRules.enabled(server)) return"));
    }
    @Test void constructionInventoryDamageAndDebugRestrictionsHaveServerGuards() throws Exception {
        assertTrue(source("game/ServerEvents.java").contains("ServerRules.enabled(serverLevel.getServer())"));
        assertTrue(source("inventory/InventoryLockEvents.java").contains("ServerRules.enabled(event.getPlayer().getServer())"));
        assertTrue(source("client/ClientAntiCheatEvents.java").contains("mc.hasSingleplayerServer()) return"));
        assertTrue(source("game/balance/Py132BalanceHandler.java").contains("ServerRules.enabled(event.getEntity().getServer())"));
    }
    @Test void localMachineWorksWithoutMatchAdmissionOrDelayedReturn() throws Exception {
        String session = source("casino/CasinoSessionManager.java");
        assertTrue(session.contains("ServerRules.enabled(player.server)) return player.isAlive()"));
        assertTrue(session.contains("player.removeTag(PLAYER_TAG);\n            return;"));
        assertTrue(session.contains("!isMenuValid(player, menu)"));
        assertTrue(session.contains("PlayerProgressManager.applyCasinoSpin"));
    }
}
