package com.makar.tacticaltablet.moderation;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModerModeManager {

    private static final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, GameType> previousGameModes = new ConcurrentHashMap<>();

    private ModerModeManager() {
    }

    public static boolean isInModerMode(ServerPlayer player) {
        return player != null && isInModerMode(player.getUUID());
    }

    public static boolean isInModerMode(UUID uuid) {
        return uuid != null && active.contains(uuid);
    }

    public static void enable(ServerPlayer player) {
        if (player == null) return;

        UUID uuid = player.getUUID();
        if (!active.contains(uuid)) {
            previousGameModes.put(uuid, player.gameMode.getGameModeForPlayer());
        }

        active.add(uuid);
        player.setGameMode(GameType.SPECTATOR);
        player.sendSystemMessage(Component.literal("[Moderation] Moder mode enabled."));
        TacticalTabletMod.LOGGER.info(
                "[Moderation] {} ({}) enabled moder mode",
                player.getGameProfile().getName(),
                player.getStringUUID()
        );
    }

    public static void disable(ServerPlayer player) {
        if (player == null) return;

        UUID uuid = player.getUUID();
        active.remove(uuid);
        GameType previous = previousGameModes.remove(uuid);
        player.setCamera(player);

        if (GameStateManager.isRunning(player.server)) {
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal(
                    "[Moderation] Moder mode disabled, but you remain spectator until the active match ends."
            ));
            TacticalTabletMod.LOGGER.info(
                    "[Moderation] {} ({}) disabled moder mode during an active match and remains spectator",
                    player.getGameProfile().getName(),
                    player.getStringUUID()
            );
            return;
        }

        player.setGameMode(previous == null ? GameType.SURVIVAL : previous);
        player.sendSystemMessage(Component.literal("[Moderation] Moder mode disabled."));
        TacticalTabletMod.LOGGER.info(
                "[Moderation] {} ({}) disabled moder mode",
                player.getGameProfile().getName(),
                player.getStringUUID()
        );
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        active.remove(player.getUUID());
        previousGameModes.remove(player.getUUID());
    }

    public static void resetAll() {
        active.clear();
        previousGameModes.clear();
    }
}
