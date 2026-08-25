package com.makar.tacticaltablet.game.lobby;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchAdmissionManager;
import com.makar.tacticaltablet.airdrop.AirdropManager;
import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.lifecycle.PlayerLifecycleSanitizer;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.chaos.ChaosSetManager;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.moderation.ModerModeManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.ChaosStatePacket;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;


public class LobbyManager {

    private static final double LOBBY_PLAYER_Y = 69.0D;

    public static void moveToLobby(ServerPlayer player) {
        moveToLobby(player, false);
    }

    /** Completes an ordinary death transition; the temporary death-screen spectator mode is not preserved. */
    public static void moveRespawningPlayerToLobby(ServerPlayer player) {
        moveToLobby(player, true);
    }

    private static void moveToLobby(ServerPlayer player, boolean ordinaryRespawn) {
        if (player == null) return;
        if (MatchAdmissionManager.enforceLateSpectator(player, false)) return;
        boolean releasedLateSpectator = MatchAdmissionManager.releaseLateSpectatorAfterMatch(player);

        ServerLevel lobby = GameStateManager.getLobbyLevel(player.server);
        if (lobby == null) {
            player.sendSystemMessage(Component.translatable("message.tacticaltablet.lobby.dimension_missing"));
            return;
        }

        relaxLobbyBorder(lobby);

        RtpTimerManager.cancel(player);
        boolean matchRunningOrStarting = GameStateManager.isRunning(player.server)
                || GameStateManager.isStartTransitionPlayerSetup();
        boolean preserveTeamMatchState = matchRunningOrStarting
                && GameStateManager.getCurrentMode().isTeamMode()
                && TeamMatchManager.getTeam(player) != null
                && LivesManager.canContinueMatch(player);
        if (!preserveTeamMatchState) {
            PlayerTabletState.reset(player);
        }
        GameType currentGameMode = player.gameMode.getGameModeForPlayer();
        boolean forcedSpectator = LivesManager.isEliminated(player)
                || ClanWarManager.shouldKeepSpectating(player)
                || MatchAdmissionManager.isLateSpectator(player);
        GameType targetGameMode = LobbyGameModePolicy.target(
                currentGameMode,
                ModerModeManager.isInModerMode(player),
                forcedSpectator,
                ordinaryRespawn || releasedLateSpectator
        );
        if (targetGameMode != currentGameMode) {
            player.setGameMode(targetGameMode);
        }

        player.removeTag("war.playing");
        InventoryManager.clearInventory(player);
        PlayerLifecycleSanitizer.clearPreviousLifeState(player);

        boolean matchRunning = matchRunningOrStarting;
        boolean canUseTabletNow = GameStateManager.isTabletAvailableInLobby(player.server)
                && LivesManager.canContinueMatch(player);

        if (matchRunning && canUseTabletNow) {
            player.addTag("in_lobby");
        } else {
            player.removeTag("in_lobby");
        }

        player.changeDimension(lobby);
        player.teleportTo(lobby, 0.5, LOBBY_PLAYER_Y, 0.5, player.getYRot(), player.getXRot());
        PlayerLifecycleSanitizer.restoreLobbySafety(player);

        if (canUseTabletNow) {
            InventoryManager.giveFreshTablet(player);
            AirdropManager.giveCompassToJoiningPlayer(player);
            if (matchRunning && (!MapSetManager.isChaosSet() || !ChaosSetManager.requiresSelection(player))) {
                RtpTimerManager.start(player);
            } else {
                sync(player);
            }
        } else {
            sync(player);
        }
    }

    public static boolean isMatchParticipantCandidate(ServerPlayer player) {
        return player != null
                && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                && !ModerModeManager.isInModerMode(player)
                && !LivesManager.isEliminated(player)
                && !ClanWarManager.shouldKeepSpectating(player);
    }

    public static void normalizeAfterMatch(ServerPlayer player, boolean wasParticipant) {
        if (player == null || !wasParticipant || ModerModeManager.isInModerMode(player)) return;
        player.setGameMode(GameType.SURVIVAL);
    }

    public static void tick(MinecraftServer server) {
        ServerLevel lobby = GameStateManager.getLobbyLevel(server);
        if (lobby == null) return;
        double rescueY = lobby.getMinBuildHeight() + 1.0D;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (GameStateManager.isInLobby(player) && player.getY() < rescueY) {
                player.teleportTo(lobby, 0.5D, LOBBY_PLAYER_Y, 0.5D, player.getYRot(), player.getXRot());
                PlayerLifecycleSanitizer.restoreLobbySafety(player);
            }
        }
    }

    public static void keepLobbyWeatherClear(MinecraftServer server) {
        if (server == null) return;

        ServerLevel lobby = GameStateManager.getLobbyLevel(server);
        if (lobby != null) {
            clearWeather(lobby);
            relaxLobbyBorder(lobby);
        }

    }

    private static void clearWeather(ServerLevel level) {
        level.setWeatherParameters(20 * 60 * 10, 0, false, false);
    }

    private static void relaxLobbyBorder(ServerLevel lobby) {
        WorldBorder border = lobby.getWorldBorder();
        border.setCenter(0.0D, 0.0D);
        border.setSize(59_999_968.0D);
        border.setDamageSafeZone(59_999_968.0D);
        border.setDamagePerBlock(0.0D);
        border.setWarningBlocks(0);
        border.setWarningTime(0);
    }

    public static void giveTabletIfMissing(ServerPlayer player) {
        InventoryManager.giveTabletIfMissing(player);
    }

    /** The welcome presentation is a login event, not a lobby-teleport side effect. */
    public static void showWelcomeOnJoin(ServerPlayer player) {
        if (player == null) return;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("message.tacticaltablet.lobby.welcome.title")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable("message.tacticaltablet.lobby.welcome.subtitle")));
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.MASTER, 1.0F, 1.0F);
    }

    public static void sync(ServerPlayer player) {
        if (player == null) return;

        PacketHandler.sendToPlayer(player, ClassXPManager.createStatePacket(player));
        PacketHandler.sendToPlayer(player, new ChaosStatePacket(ChaosSetManager.snapshot(player)));
        ContractManager.syncSelection(player);
    }
}

