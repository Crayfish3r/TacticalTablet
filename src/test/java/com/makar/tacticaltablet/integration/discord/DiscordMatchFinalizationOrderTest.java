package com.makar.tacticaltablet.integration.discord;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordMatchFinalizationOrderTest {
    private static final Path SERVER_EVENTS = Path.of(
            "src/main/java/com/makar/tacticaltablet/game/ServerEvents.java"
    );
    private static final Path GAME_STATE_MANAGER = Path.of(
            "src/main/java/com/makar/tacticaltablet/game/GameStateManager.java"
    );
    private static final Path DISCORD_SERVICE = Path.of(
            "src/main/java/com/makar/tacticaltablet/integration/discord/DiscordLeaderboardService.java"
    );

    @Test
    void deathProcessingCompletesBeforeMatchEndCheck() throws IOException {
        String source = Files.readString(SERVER_EVENTS);
        String onDeath = block(source, "public static void onDeath", "private static boolean isActiveMatchParticipant");
        String processDeath = block(source, "private static void processPlayerDeath", "private static void processKillerConsequences");
        String killerConsequences = block(source, "private static void processKillerConsequences", "private static void banTeamKiller");

        assertTrue(onDeath.indexOf("processPlayerDeath(victim, event.getSource())")
                < onDeath.indexOf("GameStateManager.checkForMatchEnd(victim.server)"));
        assertTrue(processDeath.contains("DiscordLeaderboardService.recordMatchDeath(victim)"));
        assertTrue(processDeath.contains("LivesManager.handleDeath(victim)"));
        assertTrue(processDeath.contains("processKillerConsequences(victim, source, killer)"));
        assertTrue(processDeath.contains("ContractManager.onPlayerKilled(victim, killer)"));
        assertTrue(killerConsequences.indexOf("DiscordLeaderboardService.recordMatchKill(killer)")
                < killerConsequences.indexOf("PlayerProgressManager.addCoins(killer, PlayerProgressManager.KILL_COIN_REWARD)"));
    }

    @Test
    void winRewardsAreCommittedBeforeDiscordSnapshot() throws IOException {
        String source = Files.readString(GAME_STATE_MANAGER);
        String endGame = block(source, "private static void endGame(MinecraftServer server, List<ServerPlayer>", "private static List<ServerPlayer> normalizedWinners");

        assertTrue(endGame.indexOf("PlayerProgressManager.addWin(winner)")
                < endGame.indexOf("DiscordLeaderboardService.sendCurrentMatchLeaderboard"));
        assertTrue(endGame.indexOf("PlayerProgressManager.addCoins(winner, PlayerProgressManager.WIN_COIN_REWARD)")
                < endGame.indexOf("DiscordLeaderboardService.sendCurrentMatchLeaderboard"));
    }

    @Test
    void setRewardIsCommittedBeforeSetReportAndCurrentStatsClear() throws IOException {
        String source = Files.readString(DISCORD_SERVICE);
        String sendCurrent = block(source, "public static synchronized SetWinner sendCurrentMatchLeaderboard", "static synchronized List<MatchPlayerStatsSnapshot> finalizeMatchStatistics");
        String setReportBranch = block(sendCurrent, "SetWinner setWinner = findSetWinner(server)", "deleteSetState()");

        assertTrue(sendCurrent.indexOf("finalizeMatchStatistics(server)")
                < sendCurrent.indexOf("currentMatchStats.clear()"));
        assertTrue(setReportBranch.indexOf("PlayerProgressManager.addCoins(server, setWinner.uuid(), setWinner.name(), 100)")
                < setReportBranch.indexOf("sendCurrentSetReport(server)"));
        assertTrue(setReportBranch.indexOf("sendCurrentSetReport(server)")
                < setReportBranch.indexOf("currentSetStats.clear()"));
    }

    @Test
    void discordMatchRowsUseActualBalanceNotRewardFormula() throws IOException {
        String source = Files.readString(DISCORD_SERVICE);

        assertTrue(source.contains("PlayerProgressManager.getCoins(server, playerId, name)"));
        assertTrue(source.contains("stats.coinBalance()"));
        assertTrue(!source.contains("kills * PlayerProgressManager.KILL_COIN_REWARD"));
    }

    private static String block(String source, String startNeedle, String endNeedle) {
        int start = source.indexOf(startNeedle);
        int end = source.indexOf(endNeedle, start);
        assertTrue(start >= 0, startNeedle);
        assertTrue(end > start, endNeedle);
        return source.substring(start, end);
    }
}
