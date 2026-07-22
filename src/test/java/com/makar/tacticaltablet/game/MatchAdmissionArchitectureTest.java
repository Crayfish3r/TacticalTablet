package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchAdmissionArchitectureTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void loginClassRtpRespawnAndCloneAllHaveServerSideGuards() throws IOException {
        String events = read("game/ServerEvents.java");
        String tablet = read("tablet/net/TabletPacket.java");
        String rtp = read("game/respawn/RtpTimerManager.java");
        String respawn = read("game/respawn/DeathTransitionManager.java");

        assertTrue(events.contains("enforceLateSpectator(player, true)"));
        assertTrue(events.contains("MatchAdmissionManager.isLateSpectator(newPlayer)"));
        assertTrue(tablet.contains("enforceLateSpectator(player, false)"));
        assertTrue(rtp.contains("MatchAdmissionManager.isLateSpectator(player)"));
        assertTrue(respawn.contains("enforceLateSpectator(player, false)"));
    }

    @Test
    void teamLivesCameraAndWinnerPathsUseAdmissionState() throws IOException {
        String teams = read("game/team/TeamMatchManager.java");
        String lives = read("game/lives/LivesManager.java");
        String camera = read("game/SpectatorCameraManager.java");
        String game = read("game/GameStateManager.java");

        assertTrue(teams.contains("MatchAdmissionManager.isLateSpectator(player)"));
        assertTrue(teams.contains("removePlayerFromMatch"));
        assertTrue(lives.contains("MatchAdmissionManager.isCurrentMatchParticipant"));
        assertTrue(lives.contains("clearForLateSpectator"));
        assertTrue(camera.contains("MatchAdmissionManager.isLateSpectator(player)"));
        assertTrue(game.contains("MatchAdmissionManager.isCurrentMatchParticipant(winner.getUUID())"));
    }

    @Test
    void currentMatchStatisticsRejectLateUuidWithoutClearingSetHistory() throws IOException {
        String statistics = read("integration/discord/DiscordLeaderboardService.java");

        assertTrue(statistics.contains("MatchAdmissionManager.isCurrentMatchParticipant(playerId)"));
        assertTrue(statistics.contains("MatchAdmissionManager.isCurrentMatchParticipant(attacker.getUUID())"));
        assertTrue(statistics.contains("MatchAdmissionManager.isCurrentMatchParticipant(player.getUUID())"));
        assertFalse(statistics.contains("currentSetStats.remove"));
        assertFalse(statistics.contains("currentSetGames.remove"));
    }

    @Test
    void lateJoinNotificationHasRequiredTextAndTiming() throws IOException {
        String admission = read("game/MatchAdmissionManager.java");

        assertTrue(admission.contains("ПОЗДНЕЕ ПОДКЛЮЧЕНИЕ"));
        assertTrue(admission.contains("Возрождение — только в следующей игре!"));
        assertTrue(admission.contains("new ClientboundSetTitlesAnimationPacket(10, 580, 10)"));
    }

    @Test
    void publicZonePhaseIsOneBasedAndInactiveStateIsExplicit() throws IOException {
        String zone = read("game/zone/ZoneManager.java");

        assertTrue(zone.contains("public static OptionalInt getCurrentPhaseNumber()"));
        assertTrue(zone.contains("return OptionalInt.empty()"));
        assertTrue(zone.contains("PHASES[currentPhaseIndex].number"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relativePath));
    }
}
