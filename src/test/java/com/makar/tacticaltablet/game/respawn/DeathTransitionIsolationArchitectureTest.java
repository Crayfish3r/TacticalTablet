package com.makar.tacticaltablet.game.respawn;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathTransitionIsolationArchitectureTest {
    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/makar/tacticaltablet").resolve(relative));
    }

    @Test
    void ordinaryDeathAndRespawnOnlyMutateTheVictim() throws IOException {
        String lives = source("game/lives/LivesManager.java");
        String ordinaryDeath = lives.substring(
                lives.indexOf("public static int handleDeath(ServerPlayer victim)"),
                lives.indexOf("private static int handleClanWarDeath"));
        String transition = source("game/respawn/DeathTransitionManager.java");

        assertTrue(ordinaryDeath.contains("victim.removeTag(\"war.playing\")"));
        assertTrue(ordinaryDeath.contains("victim.addTag(\"in_lobby\")"));
        assertFalse(ordinaryDeath.contains("for (ServerPlayer"));
        assertFalse(transition.contains("getOnlineTeamMembers"));
        assertTrue(transition.contains("LobbyManager.moveToLobby(player)"));
        assertTrue(transition.contains("no teammates are transitioned"));
    }

    @Test
    void groupLobbyTransitionRemainsConfinedToClanWarRegroup() throws IOException {
        String lives = source("game/lives/LivesManager.java");
        int regroupStart = lives.indexOf("private static void regroupClan");
        int regroupEnd = lives.indexOf("private static void eliminateClan", regroupStart);
        String regroup = lives.substring(regroupStart, regroupEnd);

        assertTrue(regroup.contains("for (ServerPlayer player"));
        assertTrue(regroup.contains("LobbyManager.moveToLobby(player)"));
        assertTrue(lives.indexOf("private static int handleClanWarDeath") < regroupStart);
    }
}
