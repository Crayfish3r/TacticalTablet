package com.makar.tacticaltablet.integration.online;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.integration.discord.DiscordWebhookClient;
import com.makar.tacticaltablet.integration.discord.DiscordWebhookClient.DiscordEmbed;
import com.makar.tacticaltablet.map.MapRotationManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class OnlineWebhookService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static long serverStartedAtMillis;
    private static int tickCounter;
    private static boolean requestInFlight;

    private OnlineWebhookService() {
    }

    public static synchronized void onServerStarted(MinecraftServer server) {
        OnlineWebhookConfig.reload(server);
        serverStartedAtMillis = System.currentTimeMillis();
        tickCounter = 0;
        requestInFlight = false;
        queueUpdate(server, true);
    }

    public static synchronized void onServerStopped() {
        requestInFlight = false;
        tickCounter = 0;
    }

    public static void tick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = event.getServer();

        if (server == null) {
            return;
        }

        OnlineWebhookConfig config = OnlineWebhookConfig.get(server);

        if (!config.isEnabled() || !config.hasWebhook()) {
            return;
        }

        int intervalTicks = Math.max(15, config.getUpdateIntervalSeconds()) * 20;

        synchronized (OnlineWebhookService.class) {
            if (++tickCounter < intervalTicks) {
                return;
            }

            tickCounter = 0;
        }

        queueUpdate(server, false);
    }

    public static boolean sendUpdateNow(MinecraftServer server) {
        return queueUpdate(server, true);
    }

    private static boolean queueUpdate(MinecraftServer server, boolean force) {
        if (server == null) {
            return false;
        }

        OnlineWebhookConfig config = OnlineWebhookConfig.get(server);

        if (!config.isEnabled() || !config.hasWebhook()) {
            return false;
        }

        synchronized (OnlineWebhookService.class) {
            if (requestInFlight && !force) {
                return false;
            }

            requestInFlight = true;
        }

        DiscordEmbed message = buildOnlineEmbed(server, config);
        String messageId = config.getMessageId();

        if (messageId.isBlank()) {
            DiscordWebhookClient.sendEmbedAndGetIdAsync(config.getWebhookUrl(), message)
                    .thenAccept(newMessageId -> {
                        if (newMessageId != null && !newMessageId.isBlank()) {
                            OnlineWebhookConfig.setMessageId(newMessageId);
                        }

                        finishRequest();
                    });
            return true;
        }

        DiscordWebhookClient.editEmbedAsync(config.getWebhookUrl(), messageId, message)
                .thenAccept(response -> {
                    if (!response.success()) {
                        TacticalTabletMod.LOGGER.warn(
                                "Online Discord webhook edit failed. HTTP status={}",
                                response.statusCode()
                        );

                        if (response.shouldResetMessageId()) {
                            OnlineWebhookConfig.clearMessageId();
                        }
                    }

                    finishRequest();
                });
        return true;
    }

    private static DiscordEmbed buildOnlineEmbed(MinecraftServer server, OnlineWebhookConfig config) {
        int online = server.getPlayerList().getPlayerCount();
        int max = server.getPlayerList().getMaxPlayers();
        MapInfo mapInfo = readMapInfo(server);

        StringBuilder description = new StringBuilder();
        description.append("**Онлайн:** `").append(online).append('/').append(max).append("`\n");
        description.append("**Карта:** `").append(mapInfo.currentMap()).append("`\n");
        description.append("**Следующая карта:** `").append(mapInfo.nextMap()).append("`\n");
        description.append("**До рестарта:** `").append(formatRestartCountdown(config)).append("`\n");

        if (mapInfo.armed()) {
            description.append("\nРотация подготовлена к следующему выключению.\n");
        }

        if (config.isShowPlayerNames()) {
            appendPlayerList(description, server, config);
        }

        String footer = "Обновлено: " + LocalDateTime.now().format(TIME_FORMATTER);
        int color = online > 0 ? 0x2ECC71 : 0x95A5A6;
        return new DiscordEmbed(config.getDisplayName(), description.toString(), color, footer);
    }

    private static synchronized void finishRequest() {
        requestInFlight = false;
    }

    private static String buildOnlineMessage(MinecraftServer server, OnlineWebhookConfig config) {
        int online = server.getPlayerList().getPlayerCount();
        int max = server.getPlayerList().getMaxPlayers();
        MapInfo mapInfo = readMapInfo(server);

        StringBuilder builder = new StringBuilder();
        builder.append("🟢 **").append(config.getDisplayName()).append("**").append('\n').append('\n');
        builder.append("**Онлайн:** ").append(online).append('/').append(max).append('\n');
        builder.append("**Карта:** ").append(mapInfo.currentMap()).append('\n');
        builder.append("**Следующая карта:** ").append(mapInfo.nextMap()).append('\n');
        builder.append("**До рестарта:** ").append(formatRestartCountdown(config)).append('\n');

        if (mapInfo.armed()) {
            builder.append("**Ротация:** подготовлена к следующему выключению").append('\n');
        }

        if (config.isShowPlayerNames()) {
            appendPlayerList(builder, server, config);
        }

        builder.append('\n');
        builder.append("_Обновлено: ").append(LocalDateTime.now().format(TIME_FORMATTER)).append('_');

        return DiscordWebhookClient.trimContent(builder.toString());
    }

    private static void appendPlayerList(StringBuilder builder, MinecraftServer server, OnlineWebhookConfig config) {
        List<String> names = server.getPlayerList()
                .getPlayers()
                .stream()
                .map(OnlineWebhookService::playerName)
                .filter(name -> !name.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        int limit = Math.max(0, config.getMaxDisplayedPlayers());
        builder.append('\n');

        if (names.isEmpty()) {
            builder.append("**Игроки:** никого нет онлайн").append('\n');
            return;
        }

        builder.append("**Игроки:**").append('\n');

        int shown = Math.min(limit, names.size());

        for (int i = 0; i < shown; i++) {
            builder.append("• ").append(names.get(i)).append('\n');
        }

        if (shown < names.size()) {
            builder.append("• … ещё ").append(names.size() - shown).append('\n');
        }
    }

    private static String formatRestartCountdown(OnlineWebhookConfig config) {
        int restartIntervalMinutes = config.getRestartIntervalMinutes();

        if (restartIntervalMinutes <= 0 || serverStartedAtMillis <= 0) {
            return "неизвестно";
        }

        long intervalMillis = restartIntervalMinutes * 60_000L;
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - serverStartedAtMillis);
        long remainingMillis = intervalMillis - (elapsedMillis % intervalMillis);

        if (remainingMillis <= 1_000L) {
            remainingMillis = intervalMillis;
        }

        long totalSeconds = remainingMillis / 1_000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0) {
            return String.format(Locale.ROOT, "%dч %02dм %02dс", hours, minutes, seconds);
        }

        return String.format(Locale.ROOT, "%dм %02dс", minutes, seconds);
    }

    private static MapInfo readMapInfo(MinecraftServer server) {
        try {
            MapRotationManager.RotationStatus status = MapRotationManager.getStatus(server);
            String currentMap = firstNonBlank(status.currentMap(), fallbackWorldName(server));
            String nextMap = firstNonBlank(status.nextMap(), "неизвестно");
            return new MapInfo(currentMap, nextMap, status.armed());
        } catch (RuntimeException exception) {
            TacticalTabletMod.LOGGER.warn("Failed to read map rotation status for online webhook", exception);
            return new MapInfo(fallbackWorldName(server), "неизвестно", false);
        }
    }

    private static String fallbackWorldName(MinecraftServer server) {
        if (server == null) {
            return "world";
        }

        try {
            return server.getWorldPath(LevelResource.ROOT).getFileName().toString();
        } catch (RuntimeException exception) {
            return "world";
        }
    }

    private static String playerName(ServerPlayer player) {
        if (player == null || player.getGameProfile() == null) {
            return "";
        }

        return Objects.toString(player.getGameProfile().getName(), "").trim();
    }

    private static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }

        return fallback == null || fallback.isBlank() ? "-" : fallback;
    }

    private record MapInfo(String currentMap, String nextMap, boolean armed) {
    }
}
