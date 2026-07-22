package com.makar.tacticaltablet.game.respawn;

import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.MatchAdmissionManager;
import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.makar.tacticaltablet.game.extraction.ExtractionPointManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.tablet.net.DeathScreenPacket;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.voice.VoiceChatTeamManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class DeathTransitionManager {

    private static final int SCREEN_TICKS = 20 * 4;
    private static final int SERVER_FINISH_BUFFER_TICKS = 30;
    private static final Map<UUID, DeathMessage> pendingMessages = new HashMap<>();
    private static final Map<UUID, Integer> activeTransitions = new HashMap<>();

    private DeathTransitionManager() {
    }

    public static void recordDeath(ServerPlayer victim, DamageSource source) {
        if (victim == null) return;

        Entity attacker = source == null ? null : source.getEntity();
        Entity direct = source == null ? null : source.getDirectEntity();

        if (attacker instanceof ServerPlayer killer && !killer.getUUID().equals(victim.getUUID())) {
            pendingMessages.put(victim.getUUID(), new DeathMessage(
                    "Тебя убили :(",
                    "Причина этому — " + killer.getGameProfile().getName(),
                    PlayerProgressManager.isSadTromboneKillsEnabled(killer)
            ));
            return;
        }

        if (isSelfKill(victim, attacker, direct)) {
            pendingMessages.put(victim.getUUID(), new DeathMessage(
                    "Зачем ты это...",
                    "Умер...",
                    false
            ));
            return;
        }

        pendingMessages.put(victim.getUUID(), new DeathMessage(
                "Ты умер :(",
                readableDeathReason(source),
                false
        ));
    }

    public static boolean begin(ServerPlayer player) {
        if (player == null) return false;
        if (MatchAdmissionManager.enforceLateSpectator(player, false)) return true;

        DeathMessage message = pendingMessages.remove(player.getUUID());
        if (message == null) return false;

        activeTransitions.put(player.getUUID(), SCREEN_TICKS + SERVER_FINISH_BUFFER_TICKS);
        player.setGameMode(GameType.SPECTATOR);
        PacketHandler.sendToPlayer(player, new DeathScreenPacket(
                message.title(),
                message.subtitle(),
                SCREEN_TICKS,
                message.playSadTrombone()
        ));
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || activeTransitions.isEmpty()) return;

        Iterator<Map.Entry<UUID, Integer>> iterator = activeTransitions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int ticksLeft = entry.getValue() - 1;
            if (ticksLeft > 0) {
                entry.setValue(ticksLeft);
                continue;
            }

            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                finishRespawn(player);
            }
        }
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        pendingMessages.remove(player.getUUID());
        activeTransitions.remove(player.getUUID());
    }

    public static void clearAll() {
        pendingMessages.clear();
        activeTransitions.clear();
    }

    private static void finishRespawn(ServerPlayer player) {
        if (MatchAdmissionManager.enforceLateSpectator(player, false)) {
            ClassXPManager.sync(player);
            return;
        }
        if (LivesManager.ensureEliminatedIfOutOfLives(player)) {
            ClassXPManager.sync(player);
            return;
        }

        if (MapSetManager.isClanWarSet()) {
            if (ClanWarManager.shouldMoveToLobbyAfterDeath(player)) {
                ClanWarManager.preparePlayerForRegroup(player);
                LobbyManager.moveToLobby(player);
                ExtractionPointManager.onPlayerRespawn(player);
                ClassXPManager.sync(player);
                return;
            }
            if (ClanWarManager.shouldKeepSpectating(player)) {
                ClanWarManager.keepSpectator(player);
                ClassXPManager.sync(player);
                return;
            }
        }

        LobbyManager.moveToLobby(player);
        ExtractionPointManager.onPlayerRespawn(player);
        VoiceChatTeamManager.assignPlayerToVoiceGroup(player);
        ClassXPManager.sync(player);
    }

    private static boolean isSelfKill(ServerPlayer victim, Entity attacker, Entity direct) {
        if (victim == null) return false;

        if (attacker != null && attacker.getUUID().equals(victim.getUUID())) {
            return true;
        }

        if (direct instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            return owner != null && owner.getUUID().equals(victim.getUUID());
        }

        return false;
    }

    private static String readableDeathReason(DamageSource source) {
        if (source == null) {
            return "Причина неизвестна";
        }

        if (source.is(DamageTypes.FALL)) {
            return "Причина этому — падение";
        }
        if (source.is(DamageTypes.LAVA)) {
            return "Причина этому — лава";
        }
        if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE)) {
            return "Причина этому — огонь";
        }
        if (source.is(DamageTypes.DROWN)) {
            return "Причина этому — вода";
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return "Причина этому — взрыв";
        }

        return "Причина неизвестна";
    }

    private record DeathMessage(String title, String subtitle, boolean playSadTrombone) {
    }
}
