package com.makar.tacticaltablet.game.lobby;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchAdmissionManager;
import com.makar.tacticaltablet.airdrop.AirdropManager;
import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

public class LobbyManager {

    private static final ResourceLocation LOBBY_SPAWN_TEMPLATE = new ResourceLocation("lobby", "spawn");
    private static final BlockPos LOBBY_SPAWN_ORIGIN = new BlockPos(-10, 64, -10);
    private static final double LOBBY_PLAYER_Y = 69.0D;
    private static boolean platformReady = false;

    public static void moveToLobby(ServerPlayer player) {
        if (player == null) return;
        if (MatchAdmissionManager.enforceLateSpectator(player, false)) return;

        ServerLevel lobby = GameStateManager.getLobbyLevel(player.server);
        if (lobby == null) {
            player.sendSystemMessage(Component.literal("[WAR] Измерение лобби lobby:lobby не найдено. Проверь датапак."));
            return;
        }

        if (!platformReady) {
            ensureLobbyPlatform(lobby);
            placeLobbySpawn(lobby);
            platformReady = true;
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
        player.setGameMode(GameType.SURVIVAL);

        player.removeTag("war.playing");
        InventoryManager.clearInventory(player);

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
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 5, 255, false, false, false));

        if (canUseTabletNow) {
            InventoryManager.giveFreshTablet(player);
            AirdropManager.giveCompassToJoiningPlayer(player);
            if (matchRunning) {
                RtpTimerManager.start(player);
            } else {
                sync(player);
            }
        } else {
            sync(player);
        }
    }

    private static void ensureLobbyPlatform(ServerLevel lobby) {
        BlockPos centerFloor = new BlockPos(0, 65, 0);
        if (lobby.getBlockState(centerFloor).isAir()) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = new BlockPos(x, 65, z);
                    if (lobby.getBlockState(pos).isAir()) {
                        lobby.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                    }
                }
            }
        }

        BlockPos lightPos = new BlockPos(0, 66, 0);
        if (lobby.getBlockState(lightPos).isAir()) {
            lobby.setBlock(
                    lightPos,
                    Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15),
                    3
            );
        }
    }

    private static void placeLobbySpawn(ServerLevel lobby) {
        Optional<StructureTemplate> template = lobby.getStructureManager().get(LOBBY_SPAWN_TEMPLATE);
        if (template.isEmpty()) return;

        template.get().placeInWorld(
                lobby,
                LOBBY_SPAWN_ORIGIN,
                LOBBY_SPAWN_ORIGIN,
                new StructurePlaceSettings(),
                RandomSource.create(),
                3
        );
    }

    public static void keepLobbyWeatherClear(MinecraftServer server) {
        if (server == null) return;

        ServerLevel lobby = GameStateManager.getLobbyLevel(server);
        if (lobby != null) {
            clearWeather(lobby);
            relaxLobbyBorder(lobby);
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            clearWeather(overworld);
        }
    }

    private static void clearWeather(ServerLevel level) {
        level.setWeatherParameters(20 * 60 * 10, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
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

    public static void sync(ServerPlayer player) {
        if (player == null) return;

        PacketHandler.sendToPlayer(player, ClassXPManager.createStatePacket(player));
        ContractManager.syncSelection(player);
    }
}

