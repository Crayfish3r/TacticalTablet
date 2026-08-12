package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLifecycleArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void sanitizerSeparatesPreviousLifeCleanupFromDeploymentAndEmergencyRepair() throws IOException {
        String source = source("game/lifecycle/PlayerLifecycleSanitizer.java");

        String previousLife = method(source, "clearPreviousLifeState", "prepareForDeployment");
        String deployment = method(source, "prepareForDeployment", "restoreLobbySafety");
        String emergency = method(source, "restoreLobbySafety", "private static void resetTransientState");
        String reset = source.substring(source.indexOf("private static void resetTransientState"));

        assertTrue(previousLife.contains("CuriosInventoryBridge.clear(player)"));
        assertFalse(deployment.contains("CuriosInventoryBridge"));
        assertFalse(emergency.contains("CuriosInventoryBridge"));
        assertTrue(reset.contains("removeAllEffects()"));
        assertTrue(reset.contains("setAbsorptionAmount(0.0F)"));
        assertTrue(reset.contains("clearFire()"));
        assertTrue(reset.contains("setTicksFrozen(0)"));
        assertTrue(reset.contains("fallDistance = 0.0F"));
        assertTrue(reset.contains("setAirSupply(player.getMaxAirSupply())"));
        assertTrue(reset.contains("player.setHealth(maxHealth)"));
        assertFalse(reset.contains("20.0F"));
    }

    @Test
    void lobbyAndDeploymentUseAuthoritativeLifecycleBoundaries() throws IOException {
        String lobby = source("game/lobby/LobbyManager.java");
        String rtp = source("game/respawn/RtpTimerManager.java");
        String finishRtp = rtp.substring(rtp.indexOf("private static void finishRtp"));

        assertOrdered(lobby,
                "InventoryManager.clearInventory(player)",
                "PlayerLifecycleSanitizer.clearPreviousLifeState(player)",
                "player.changeDimension(lobby)",
                "PlayerLifecycleSanitizer.restoreLobbySafety(player)");
        assertFalse(lobby.contains("MobEffects.DAMAGE_RESISTANCE"));
        assertFalse(lobby.contains("player.addEffect("));

        assertOrdered(finishRtp,
                "PlayerLifecycleSanitizer.prepareForDeployment(player)",
                "LivesManager.ensureStarted(player)",
                "PostRtpProtectionManager.grant(player",
                "player.removeTag(\"in_lobby\")",
                "player.addTag(\"war.playing\")");
        assertFalse(finishRtp.contains("clearPreviousLifeState"));
    }

    @Test
    void deathSpectatorAndLobbyGuardsCoverPreviousLifeState() throws IOException {
        String events = source("game/ServerEvents.java");
        String transition = source("game/respawn/DeathTransitionManager.java");
        String lives = source("game/lives/LivesManager.java");

        assertTrue(events.contains("onLobbyEffectApplicable(MobEffectEvent.Applicable event)"));
        assertTrue(events.contains("event.setResult(Event.Result.DENY)"));
        assertTrue(events.contains("onLobbyDeath(LivingDeathEvent event)"));
        assertTrue(events.contains("PlayerLifecycleSanitizer.restoreLobbySafety(player)"));
        assertTrue(events.contains("GameStateManager.isInLobby(player)"));
        assertTrue(events.contains("!GameStateManager.isInLobby(player) && !PostRtpProtectionManager.isProtected(player)"));
        assertTrue(transition.contains("PlayerLifecycleSanitizer.clearPreviousLifeState(player)"));
        assertTrue(lives.contains("PlayerLifecycleSanitizer.clearPreviousLifeState(player)"));
    }

    @Test
    void optionalCuriosBridgeKeepsCuriosTypesBehindTheLoaderGuard() throws IOException {
        String bridge = source("integration/curios/CuriosInventoryBridge.java");
        String build = Files.readString(Path.of("build.gradle"));
        String mods = Files.readString(Path.of("src/main/resources/META-INF/mods.toml"));

        String commonApi = bridge.substring(0, bridge.indexOf("private static final class CuriosLoaded"));
        assertTrue(commonApi.contains("ModList.get().isLoaded(\"curios\")"));
        assertFalse(commonApi.contains("top.theillusivec4.curios"));
        assertTrue(bridge.contains("CuriosApi.getCuriosInventory(player)"));
        assertFalse(bridge.contains("java.lang.reflect"));
        assertFalse(bridge.contains("top.theillusivec4.curios.common"));
        assertTrue(build.contains("compileOnly fg.deobf(\"top.theillusivec4.curios:curios-forge:${curios_version}\")"));

        int curiosSectionStart = mods.indexOf("modId=\"curios\"");
        assertTrue(curiosSectionStart >= 0);
        String curiosSection = mods.substring(curiosSectionStart);
        assertTrue(curiosSection.contains("mandatory=false"));
        assertTrue(curiosSection.contains("side=\"BOTH\""));
    }

    private static String method(String source, String start, String end) {
        return source.substring(source.indexOf(start), source.indexOf(end));
    }

    private static void assertOrdered(String source, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = source.indexOf(marker);
            assertTrue(current > previous, () -> "Expected ordered marker: " + marker);
            previous = current;
        }
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath)).replace("\r\n", "\n");
    }
}
