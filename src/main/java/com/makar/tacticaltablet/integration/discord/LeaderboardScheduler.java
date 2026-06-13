package com.makar.tacticaltablet.integration.discord;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class LeaderboardScheduler {

    private static int tickCounter;
    private static LocalDate lastSentDate;

    private LeaderboardScheduler() {
    }

    public static void onServerStarted(MinecraftServer server) {
        DiscordConfig.reload(server);
        tickCounter = 0;
        lastSentDate = null;
    }

    public static void reset() {
        tickCounter = 0;
        lastSentDate = null;
    }

    public static void tick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = event.getServer();

        if (server == null || ++tickCounter < 20) {
            return;
        }

        tickCounter = 0;
        DiscordConfig config = DiscordConfig.get(server);

        if (!config.hasWebhook()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        if (lastSentDate != null && lastSentDate.equals(today)) {
            return;
        }

        if (now.getHour() == config.getDailyHour() && now.getMinute() == config.getDailyMinute()) {
            lastSentDate = today;
            DiscordLeaderboardService.sendOverallLeaderboard(server);
        }
    }
}

