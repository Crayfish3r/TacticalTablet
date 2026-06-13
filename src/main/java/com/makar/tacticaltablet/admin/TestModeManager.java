package com.makar.tacticaltablet.admin;

import com.makar.tacticaltablet.game.GameStateManager;

import net.minecraft.server.MinecraftServer;

public class TestModeManager {

    private static boolean soloStartEnabled = false;
    private static boolean lowPlayerTeamTestsEnabled = false;

    public static boolean isSoloStartEnabled() {
        return soloStartEnabled;
    }

    public static void setSoloStartEnabled(boolean enabled) {
        soloStartEnabled = enabled;
    }

    public static void reset() {
        soloStartEnabled = false;
        lowPlayerTeamTestsEnabled = false;
    }

    public static boolean isLowPlayerTeamTestsEnabled() {
        return lowPlayerTeamTestsEnabled;
    }

    public static void setLowPlayerTeamTestsEnabled(boolean enabled) {
        lowPlayerTeamTestsEnabled = enabled;
    }

    public static boolean canBypassTeamModeMinimums() {
        return lowPlayerTeamTestsEnabled;
    }

    public static int getRequiredPlayers(int defaultMinimum) {
        return soloStartEnabled ? 1 : defaultMinimum;
    }

    public static boolean hasEnoughOnlinePlayers(MinecraftServer server, int defaultMinimum) {
        return GameStateManager.onlinePlayers(server) >= getRequiredPlayers(defaultMinimum);
    }

    public static String getStatusText() {
        String solo = soloStartEnabled
                ? "соло-тест включён. Матчи могут стартовать с 1 игроком."
                : "соло-тест выключен. Для матча нужно обычное число игроков.";
        String team = lowPlayerTeamTestsEnabled
                ? " Тесты голосования/команд с малым числом игроков включены."
                : " Тесты голосования/команд с малым числом игроков выключены.";
        return solo + team;
    }
}

