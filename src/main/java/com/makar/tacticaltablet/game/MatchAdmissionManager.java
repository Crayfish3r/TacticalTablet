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
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.progression.PassiveClassXPManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;
import com.makar.tacticaltablet.voice.VoiceChatTeamManager;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public final class MatchAdmissionManager {
    private static final String DATA_LATE_NOTIFICATION_MATCH =
            "tacticaltablet.late_admission_notification_match";
    private static final String DATA_LATE_SPECTATOR_MATCH =
            "tacticaltablet.late_spectator_match";
    private static final Component LATE_JOIN_TITLE = Component.literal("ПОЗДНЕЕ ПОДКЛЮЧЕНИЕ");
    private static final Component LATE_JOIN_SUBTITLE =
            Component.literal("Возрождение — только в следующей игре!");
    private static final MatchAdmissionWindow ADMISSION_WINDOW = new MatchAdmissionWindow();

    private static final MatchAdmissionService ADMISSION_SERVICE = new MatchAdmissionService(
            GameStateManager::getLifecycleSnapshot,
            ADMISSION_WINDOW::snapshot,
            GameStateManager::registerCurrentMatchParticipant
    );

    private MatchAdmissionManager() {
    }

    public static MatchAdmissionStatus resolve(ServerPlayer player) {
        return inspectStatus(player);
    }

    public static MatchAdmissionStatus resolve(UUID playerId) {
        return inspectStatus(playerId);
    }

    public static MatchAdmissionStatus inspectStatus(ServerPlayer player) {
        if (player == null) return MatchAdmissionStatus.NO_ACTIVE_MATCH;
        observeServerTick(player.server.getTickCount());
        return inspectStatus(player.getUUID());
    }

    public static MatchAdmissionStatus inspectStatus(UUID playerId) {
        return ADMISSION_SERVICE.inspect(playerId).status();
    }

    public static MatchAdmissionDecision finalizePlayerJoin(ServerPlayer player) {
        if (player == null) {
            return new MatchAdmissionDecision(
                    MatchAdmissionOutcome.DISCONNECTED,
                    Optional.empty()
            );
        }

        observeServerTick(player.server.getTickCount());
        MatchAdmissionService.Admission admission = ADMISSION_SERVICE.finalizeAdmission(
                player.getUUID(),
                player::hasDisconnected
        );
        if (admission.internalFailure()) {
            TacticalTabletMod.LOGGER.error(
                    "Match admission failed safely playerId={} initialState={} initialStatus={} "
                            + "initialTick={} initialDeadline={} initialRevision={} currentState={} "
                            + "currentStatus={} currentTick={} currentDeadline={} currentRevision={} "
                            + "diagnostic={}",
                    player.getUUID(),
                    admission.initial().matchState(),
                    admission.initial().status(),
                    admission.initial().currentTick(),
                    admission.initial().deadlineTick(),
                    admission.initial().revision(),
                    admission.current().matchState(),
                    admission.current().status(),
                    admission.current().currentTick(),
                    admission.current().deadlineTick(),
                    admission.current().revision(),
                    admission.diagnostic()
            );
        }
        return new MatchAdmissionDecision(
                admission.outcome(),
                Optional.ofNullable(admission.current().matchId())
        );
    }

    /**
     * Compatibility layer for integrations compiled against the previous status-only API.
     */
    @Deprecated
    public static MatchAdmissionStatus admitEligiblePlayer(ServerPlayer player) {
        return finalizePlayerJoin(player).outcome().legacyStatus();
    }

    public static boolean isLateSpectator(ServerPlayer player) {
        return inspectStatus(player) == MatchAdmissionStatus.LATE_SPECTATOR;
    }

    public static boolean isLateSpectator(UUID playerId) {
        return inspectStatus(playerId) == MatchAdmissionStatus.LATE_SPECTATOR;
    }

    public static boolean isCurrentMatchParticipant(UUID playerId) {
        if (playerId == null) return false;
        MatchLifecycleSnapshot snapshot = GameStateManager.getLifecycleSnapshot();
        return snapshot.matchId().isPresent()
                && (snapshot.state() == MatchState.STARTING || snapshot.state() == MatchState.RUNNING)
                && snapshot.participantIds().contains(playerId);
    }

    static void openAdmissionWindow(UUID matchId, long startTick) {
        if (matchId == null) return;
        ADMISSION_WINDOW.open(matchId, startTick);
    }

    static void observeServerTick(long currentTick) {
        ADMISSION_WINDOW.advance(currentTick);
    }

    static void clearAdmissionWindow(UUID expectedMatchId) {
        ADMISSION_WINDOW.clear(expectedMatchId);
    }

    static void clearAdmissionWindow() {
        ADMISSION_WINDOW.clear(null);
    }

    public static boolean enforceLateSpectator(ServerPlayer player, boolean showNotification) {
        if (player == null || !isLateSpectator(player)) return false;
        return enforceFinalizedLateSpectator(player, showNotification);
    }

    static boolean enforceFinalizedLateSpectator(ServerPlayer player, boolean showNotification) {
        if (player == null || player.hasDisconnected()) return false;

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

        String matchKey = GameStateManager.getLifecycleSnapshot()
                .matchId()
                .map(UUID::toString)
                .orElse("no-active-match");
        player.getPersistentData().putString(DATA_LATE_SPECTATOR_MATCH, matchKey);
        String notifiedMatch = player.getPersistentData().getString(DATA_LATE_NOTIFICATION_MATCH);
        if (showNotification && !matchKey.equals(notifiedMatch)) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 580, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(LATE_JOIN_TITLE));
            player.connection.send(new ClientboundSetSubtitleTextPacket(LATE_JOIN_SUBTITLE));
            player.getPersistentData().putString(DATA_LATE_NOTIFICATION_MATCH, matchKey);
        }
        return true;
    }

    /** Releases only spectators that this manager forced for a completed or aborted match. */
    public static boolean releaseLateSpectatorAfterMatch(ServerPlayer player) {
        if (player == null || GameStateManager.isRunning(player.server)) return false;
        if (!player.getPersistentData().contains(DATA_LATE_SPECTATOR_MATCH)) return false;

        player.getPersistentData().remove(DATA_LATE_SPECTATOR_MATCH);
        return true;
    }
}
