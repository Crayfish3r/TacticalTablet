package com.makar.tacticaltablet.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryRemovalArchitectureTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");

    @Test
    void telemetryTypesAndProductionReferencesAreAbsent() throws IOException {
        assertFalse(Files.exists(MAIN_JAVA.resolve("com/makar/tacticaltablet/anticheat/AntiCheatManager.java")));
        assertFalse(Files.exists(MAIN_JAVA.resolve("com/makar/tacticaltablet/anticheat/MovementAntiCheat.java")));
        assertFalse(Files.exists(MAIN_JAVA.resolve("com/makar/tacticaltablet/anticheat/ViolationType.java")));
        assertFalse(Files.exists(MAIN_JAVA.resolve("com/makar/tacticaltablet/anticheat/Severity.java")));

        String production = readProductionSources();
        assertFalse(production.contains("AntiCheatManager"));
        assertFalse(production.contains("MovementAntiCheat"));
        assertFalse(production.contains("MOVEMENT_ANOMALY"));
        assertFalse(production.contains("COMBAT_REACH"));
    }

    @Test
    void serverTickKeepsInventoryEnforcementWithoutMovementPolling() throws IOException {
        String source = Files.readString(MAIN_JAVA.resolve("com/makar/tacticaltablet/game/ServerEvents.java"));

        assertTrue(source.contains("InventoryGuard.tick(event.getServer())"));
        assertFalse(source.contains("checkCombatReach"));
        assertFalse(source.contains("MAX_MELEE_REACH"));
    }

    @Test
    void tabletValidationAndRateLimitStillRejectBeforeAction() throws IOException {
        String source = Files.readString(MAIN_JAVA.resolve("com/makar/tacticaltablet/tablet/net/TabletPacket.java"));

        assertTrue(source.contains("if (actionId < MIN_ACTION_ID || actionId > MAX_ACTION_ID)"));
        assertTrue(source.contains("if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.TABLET))"));
        assertTrue(source.contains("if (!InventoryManager.hasTablet(player))"));
        assertTrue(source.contains("LobbyManager.sync(player);\n                return;"));
        assertTrue(source.contains("RtpTimerManager.forceRtp(player)"));
    }

    @Test
    void inventoryAndContainerActionsRemainAuthoritativelyBlocked() throws IOException {
        String events = Files.readString(MAIN_JAVA.resolve(
                "com/makar/tacticaltablet/inventory/InventoryLockEvents.java"));
        String guard = Files.readString(MAIN_JAVA.resolve(
                "com/makar/tacticaltablet/inventory/InventoryGuard.java"));

        assertTrue(events.contains("event.setCanceled(true)"));
        assertTrue(events.contains("event.setCancellationResult(InteractionResult.FAIL)"));
        assertTrue(guard.contains("InventoryManager.clearInventory(player)"));
        assertTrue(guard.contains("InventoryManager.clearTablets(player)"));
        assertTrue(guard.contains("InventoryManager.syncInventory(player)"));
    }

    @Test
    void damageAccountingRtpEligibilityAndCommandPermissionsRemainWired() throws IOException {
        String serverEvents = Files.readString(MAIN_JAVA.resolve(
                "com/makar/tacticaltablet/game/ServerEvents.java"));
        String rtpTimer = Files.readString(MAIN_JAVA.resolve(
                "com/makar/tacticaltablet/game/respawn/RtpTimerManager.java"));
        String rtpCommand = Files.readString(MAIN_JAVA.resolve(
                "com/makar/tacticaltablet/command/RtpCommand.java"));

        assertTrue(serverEvents.contains("MatchDamageAccounting.actualHealthLostFromFinalDamage"));
        assertTrue(serverEvents.contains("DiscordLeaderboardService.recordMatchDamage"));
        assertTrue(rtpTimer.contains("public static boolean canRtp(ServerPlayer player, boolean notify)"));
        assertTrue(rtpCommand.contains(".requires(source -> source.hasPermission(2))"));
    }

    private static String readProductionSources() throws IOException {
        StringBuilder result = new StringBuilder();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }
}
