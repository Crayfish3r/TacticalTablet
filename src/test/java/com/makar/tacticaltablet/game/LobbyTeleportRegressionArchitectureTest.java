package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyTeleportRegressionArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void lobbyAndRtpUseSingleCrossDimensionTeleport() throws IOException {
        String lobby = source("game/lobby/LobbyManager.java");
        String safeTeleport = source("game/teleport/SafeTeleport.java");

        assertFalse(lobby.contains("changeDimension("));
        assertFalse(safeTeleport.contains("changeDimension("));
        assertTrue(lobby.contains("player.teleportTo(lobby"));
        assertTrue(safeTeleport.contains("player.teleportTo(\n                overworld"));
    }

    @Test
    void lobbyRescueUsesValidatedSpawnAndRelativeThreshold() throws IOException {
        String lobby = source("game/lobby/LobbyManager.java");
        String tick = method(lobby, "public static void tick", "public static void keepLobbyWeatherClear");

        assertTrue(tick.contains("LobbySpawnResolver.resolve(lobby, player)"));
        assertTrue(tick.contains("LOBBY_RESCUE_Y"));
        assertFalse(tick.contains("getMinBuildHeight"));
        assertFalse(tick.contains("0.5D, LOBBY_PLAYER_Y, 0.5D"));
    }

    @Test
    void loginPathHasNoSleepThreadOrDuplicateLobbyRelocation() throws IOException {
        String events = source("game/ServerEvents.java");
        String login = method(events, "public static void onPlayerJoin", "private static void finishLateSpectatorJoin");

        assertFalse(login.contains("Thread.sleep"));
        assertFalse(login.contains("new Thread"));
        assertTrue(occurrences(login, "LobbyManager.moveToLobby(player)") == 1);
    }

    private static String method(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }

    private static int occurrences(String source, String marker) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath)).replace("\r\n", "\n");
    }
}
