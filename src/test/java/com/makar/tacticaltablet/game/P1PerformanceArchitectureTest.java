package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P1PerformanceArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void contractCountdownUsesTimerPacketAndFinalFullState() throws IOException {
        String manager = read("game/contract/ContractManager.java");
        String tick = block(manager, "public static void tick", "public static void reset");
        String syncAll = block(manager, "public static void syncSelectionAll", "public static ContractSelectionStatePacket selectionState");
        String selectTarget = block(manager, "public static boolean selectTarget", "public static void onTrackerUsed");

        assertTrue(tick.contains("syncSelectionTimerAll(server);"));
        assertTrue(tick.contains("selectionActive = false;"));
        assertTrue(tick.contains("removeUnclaimedSelectionTrackers(server);"));
        assertEquals(1, occurrences(tick, "syncSelectionAll(server);"));
        assertTrue(syncAll.contains("selectionCandidatesForBroadcast(server, players)"));
        assertTrue(selectTarget.contains("server.getPlayerList().getPlayer(targetUuid)"));
        assertTrue(selectTarget.contains("isValidTarget(owner, target)"));
    }

    @Test
    void deathXpIsDeferredUntilFinalAuthoritativeSync() throws IOException {
        String manager = read("progression/ClassXPManager.java");
        String events = read("game/ServerEvents.java");
        String addXp = block(manager, "public static int addXP(", "/**");
        String deferred = block(manager, "public static int addXPDeferredSync", "private static int addXPInternal");
        String death = block(events, "private static void processPlayerDeath", "private static TacticalKillFeed.KillReward processKillerConsequences");
        String rewards = block(events, "private static TacticalKillFeed.KillReward processKillerConsequences", "private static void banTeamKiller");

        assertTrue(addXp.contains("addXPInternal(player, clazz, amount, true)"));
        assertTrue(deferred.contains("addXPInternal(player, clazz, amount, false)"));
        assertTrue(rewards.contains("ClassXPManager.addXPDeferredSync(killer, clazz, result.xp)"));
        assertFalse(rewards.contains("ClassXPManager.addXP(killer, clazz, result.xp)"));
        assertTrue(death.indexOf("ContractManager.onPlayerKilled") < death.indexOf("ClassXPManager.sync(killer)"));
    }

    @Test
    void spectatorFastPathAvoidsFullCandidateListsAndKeepsTeamFallback() throws IOException {
        String manager = read("game/SpectatorCameraManager.java");
        String stored = block(manager, "private static ServerPlayer getStoredValidTarget", "private static ServerPlayer getCurrentValidCameraTarget");
        String current = block(manager, "private static ServerPlayer getCurrentValidCameraTarget", "private static ServerPlayer selectInitialTarget");
        String allowed = block(manager, "private static boolean isAllowedCameraTarget", "private static boolean hasAvailableTeammateCameraTarget");
        String teammateScan = block(manager, "private static boolean hasAvailableTeammateCameraTarget", "private static boolean shouldLockSpectator");

        assertFalse(stored.contains("getAvailableTargets"));
        assertFalse(current.contains("getAvailableTargets"));
        assertTrue(stored.contains("isAllowedCameraTarget"));
        assertTrue(current.contains("isAllowedCameraTarget"));
        assertTrue(allowed.contains("TeamMatchManager.areTeammates(spectator, target)"));
        assertTrue(allowed.contains("return !hasAvailableTeammateCameraTarget(spectator);"));
        assertTrue(teammateScan.contains("TeamMatchManager.getOnlineTeamMembers"));
        assertFalse(teammateScan.contains("server.getPlayerList().getPlayers()"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative)).replace("\r\n", "\n");
    }

    private static String block(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start, "Expected source markers were not found");
        return source.substring(start, end);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }
}
