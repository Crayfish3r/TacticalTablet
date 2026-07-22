package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchState;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.respawn.DeathTransitionManager;
import com.makar.tacticaltablet.game.respawn.PostRtpProtectionManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.game.zone.ZoneManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.progression.PassiveClassXPManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;
import com.makar.tacticaltablet.voice.VoiceChatTeamManager;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class MatchAdmissionManager {
    private static final Component LATE_JOIN_TITLE = Component.literal("ПОЗДНЕЕ ПОДКЛЮЧЕНИЕ");
    private static final Component LATE_JOIN_SUBTITLE =
            Component.literal("Возрождение — только в следующей игре!");

    private MatchAdmissionManager() {
    }

    public static MatchAdmissionStatus resolve(ServerPlayer player) {
        return player == null ? MatchAdmissionStatus.NO_ACTIVE_MATCH : resolve(player.getUUID());
    }

    public static MatchAdmissionStatus resolve(UUID playerId) {
        if (playerId == null) return MatchAdmissionStatus.NO_ACTIVE_MATCH;

        MatchLifecycleSnapshot snapshot = GameStateManager.getLifecycleSnapshot();
        UUID matchId = snapshot.matchId().orElse(null);
        int phase = ZoneManager.getCurrentPhaseNumber().orElse(0);
        boolean active = matchId != null
                && (snapshot.state() == MatchState.STARTING || snapshot.state() == MatchState.RUNNING);
        boolean admitted = snapshot.participantIds().contains(playerId);
        MatchAdmissionStatus status = MatchAdmissionPolicy.classify(active, admitted, phase);

        if (status == MatchAdmissionStatus.ADMITTED && !admitted) {
            if (GameStateManager.registerCurrentMatchParticipant(matchId, playerId)) {
                return MatchAdmissionStatus.ADMITTED;
            }
            TacticalTabletMod.LOGGER.error(
                    "Failed to register early match participant matchId={} playerId={} zonePhase={}",
                    matchId, playerId, phase
            );
            return MatchAdmissionStatus.LATE_SPECTATOR;
        }
        return status;
    }

    public static boolean isLateSpectator(ServerPlayer player) {
        return resolve(player) == MatchAdmissionStatus.LATE_SPECTATOR;
    }

    public static boolean isLateSpectator(UUID playerId) {
        return resolve(playerId) == MatchAdmissionStatus.LATE_SPECTATOR;
    }

    public static boolean isCurrentMatchParticipant(UUID playerId) {
        if (playerId == null) return false;
        MatchLifecycleSnapshot snapshot = GameStateManager.getLifecycleSnapshot();
        return snapshot.matchId().isPresent()
                && (snapshot.state() == MatchState.STARTING || snapshot.state() == MatchState.RUNNING)
                && snapshot.participantIds().contains(playerId);
    }

    public static boolean enforceLateSpectator(ServerPlayer player, boolean showNotification) {
        if (player == null || !isLateSpectator(player)) return false;

        RtpTimerManager.cancel(player);
        PostRtpProtectionManager.clear(player);
        DeathTransitionManager.clear(player);
        PassiveClassXPManager.clear(player);
        PlayerTabletState.reset(player);
        LivesManager.clearForLateSpectator(player);
        TeamMatchManager.removePlayerFromMatch(player);
        VoiceChatTeamManager.removePlayerFromVoiceGroup(player);
        InventoryManager.clearInventory(player);
        player.removeTag("war.playing");
        player.removeTag("in_lobby");
        player.removeTag(ClanWarManager.TAG_SPECTATING);
        player.removeTag(ClanWarManager.TAG_REGROUP_PENDING);
        LivesManager.moveEliminatedToSpectator(player);

        if (showNotification) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 580, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(LATE_JOIN_TITLE));
            player.connection.send(new ClientboundSetSubtitleTextPacket(LATE_JOIN_SUBTITLE));
        }
        return true;
    }
}
