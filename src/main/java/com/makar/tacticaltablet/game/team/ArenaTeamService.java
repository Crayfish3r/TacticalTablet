package com.makar.tacticaltablet.game.team;

import com.makar.tacticaltablet.game.*;
import com.makar.tacticaltablet.game.arena.ArenaMatchRuntime;
import com.makar.tacticaltablet.game.respawn.DeathTransitionManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.moderation.ModerModeManager;
import com.makar.tacticaltablet.tablet.net.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Server-thread roster admission and bounded, change-driven subscriptions; the roster itself lives in TeamMatchManager. */
public final class ArenaTeamService {
    public static final UUID NONE = new UUID(0, 0);
    public enum Action { FETCH, JOIN, LEAVE }
    public enum Result { OK, FULL, FORBIDDEN, STALE, INVALID }
    private static final Map<UUID, Viewer> viewers = new HashMap<>();
    private static long revision;
    private static final class Viewer {
        UUID session; Object connection; long request; Result result = Result.OK; String fingerprint = "";
        Viewer(UUID session, ServerPlayer player) { this.session = session; connection = player.connection; }
    }
    private ArenaTeamService() { }
    public static String denial(ServerPlayer p) {
        if (!MapSetManager.isArenaSet() || TeamMatchManager.arenaMatchId() == null || !ArenaMatchRuntime.combatOpen()
                || !TeamMatchManager.arenaMatchId().equals(ArenaMatchRuntime.matchId())) return "Состав сейчас изменить нельзя: бой не открыт.";
        if (!ArenaMatchRuntime.accepts(p) || !MatchAdmissionManager.isCurrentMatchParticipant(p.getUUID())
                || !p.isAlive() || p.isDeadOrDying() || p.isSpectator() || ModerModeManager.isInModerMode(p)
                || MatchAdmissionManager.isLateSpectator(p) || DeathTransitionManager.isTransitionActive(p)) return "Состав доступен только участникам между жизнями.";
        var life = ArenaMatchRuntime.life(p);
        if (life == null || life.awaitingRespawn || life.rtpUsed || !GameStateManager.isInLobby(p)) return "Менять команду можно в лобби до высадки этой жизни.";
        return InventoryManager.hasTablet(p) ? "" : "Нужен планшет в инвентаре.";
    }
    public static boolean isAnchor(ServerPlayer requester, ServerPlayer anchor) {
        if (requester == null || anchor == null || requester == anchor || !ArenaMatchRuntime.accepts(requester)
                || !ArenaMatchRuntime.accepts(anchor) || !TeamMatchManager.areTeammates(requester, anchor)
                || !Objects.equals(TeamMatchManager.arenaMatchId(), ArenaMatchRuntime.matchId())) return false;
        var life = ArenaMatchRuntime.life(anchor);
        var level = GameStateManager.getOverworld(anchor.server);
        return life != null && life.rtpUsed && !life.awaitingRespawn && anchor.isAlive() && !anchor.isDeadOrDying()
                && !anchor.isSpectator() && !MatchAdmissionManager.isLateSpectator(anchor)
                && !ModerModeManager.isInModerMode(anchor) && !DeathTransitionManager.isTransitionActive(anchor)
                && anchor.getTags().contains("war.playing") && level != null && anchor.serverLevel() == level
                && level.getWorldBorder().isWithinBounds(anchor.blockPosition());
    }
    public static boolean canDeploy(ServerPlayer p) {
        var life = ArenaMatchRuntime.life(p);
        return ArenaMatchRuntime.accepts(p) && life != null && !life.rtpUsed && !life.awaitingRespawn
                && p.isAlive() && !p.isDeadOrDying() && !p.isSpectator() && !ModerModeManager.isInModerMode(p)
                && !DeathTransitionManager.isTransitionActive(p) && GameStateManager.isInLobby(p);
    }
    public static void handle(ServerPlayer p, UUID session, long request, UUID match, Action action, int team) {
        if (p == null || request <= 0) return;
        boolean allowed = PacketHandler.allowC2S(p, action == Action.FETCH ? PacketHandler.C2SAction.TABLET_SNAPSHOT : PacketHandler.C2SAction.TABLET);
        Viewer v = viewers.get(p.getUUID());
        if (v != null && v.connection == p.connection && !v.session.equals(session)) return;
        if (v == null || v.connection != p.connection || !v.session.equals(session)) {
            v = new Viewer(session, p); viewers.put(p.getUUID(), v);
        }
        if (request <= v.request) { send(p, v, true); return; }
        v.request = request; v.result = Result.OK;
        if (!allowed) { v.result = Result.FORBIDDEN; send(p, v, true); return; }
        if (action != Action.FETCH) {
            if (!Objects.equals(match, TeamMatchManager.arenaMatchId())) v.result = Result.STALE;
            else if (!denial(p).isEmpty()) v.result = Result.FORBIDDEN;
            else if (action == Action.JOIN && (team < 0 || team >= TeamId.standardValues().length)) v.result = Result.INVALID;
            else if (!TeamMatchManager.changeArenaTeam(p, action == Action.LEAVE ? null : TeamId.standardValues()[team])) v.result = Result.FULL;
        }
        send(p, v, true); tick(p.server);
    }
    public static void tick(MinecraftServer server) {
        for (var entry : new ArrayList<>(viewers.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || p.hasDisconnected() || p.connection != entry.getValue().connection) viewers.remove(entry.getKey());
            else send(p, entry.getValue(), false);
        }
    }
    private static void send(ServerPlayer p, Viewer v, boolean force) {
        List<ArenaTeamStatePacket.Member> members = new ArrayList<>();
        for (TeamId team : TeamId.standardValues()) for (UUID id : TeamMatchManager.arenaMembers(team)) {
            ServerPlayer member = p.server.getPlayerList().getPlayer(id);
            String name = member == null ? TeamMatchManager.teamNameSnapshot(p.server).get(team).get(TeamMatchManager.arenaMembers(team).indexOf(id)) : member.getGameProfile().getName();
            var life = member == null ? null : ArenaMatchRuntime.life(member);
            String status = member == null ? "не в сети" : life != null && life.rtpUsed && !life.awaitingRespawn
                    && member.getTags().contains("war.playing") && !GameStateManager.isInLobby(member) ? "на карте" : "в лобби/возрождается";
            members.add(new ArenaTeamStatePacket.Member(team.ordinal(), id, name, status));
        }
        boolean arena = MapSetManager.isArenaSet();
        UUID match = Optional.ofNullable(TeamMatchManager.arenaMatchId()).orElse(NONE);
        int own = TeamMatchManager.getTeam(p) == null ? -1 : TeamMatchManager.getTeam(p).ordinal();
        String reason = denial(p);
        String fingerprint = arena + "|" + match + "|" + own + "|" + reason + "|" + members;
        if (!force && fingerprint.equals(v.fingerprint)) return;
        v.fingerprint = fingerprint;
        PacketHandler.sendToPlayer(p, new ArenaTeamStatePacket(v.session, v.request, match, ++revision, arena, own, reason, v.result, members));
    }
    public static void reset() { viewers.clear(); }
}
