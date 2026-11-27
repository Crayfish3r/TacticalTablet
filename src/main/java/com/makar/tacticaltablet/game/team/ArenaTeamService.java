package com.makar.tacticaltablet.game.team;

import com.makar.tacticaltablet.game.*;
import com.makar.tacticaltablet.game.arena.ArenaMatchRuntime;
import com.makar.tacticaltablet.game.respawn.DeathTransitionManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.moderation.ModerModeManager;
import com.makar.tacticaltablet.tablet.net.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.*;

/** Server-thread roster admission and request/response snapshots. */
public final class ArenaTeamService {
    public static final UUID NONE = new UUID(0, 0);
    public enum Action { FETCH, JOIN, LEAVE }
    public enum Result { OK, FULL, FORBIDDEN, STALE, INVALID }
    private static final Map<ServerGamePacketListenerImpl, Requests> connections = new HashMap<>();
    private static long revision;
    private static final class Requests {
        final UUID session;
        long lastRequest;

        Requests(UUID session) { this.session = session; }
    }
    private ArenaTeamService() { }
    public static String denial(ServerPlayer p) {
        if (TeamMatchManager.currentArenaRoster() == null || !ArenaMatchRuntime.combatOpen())
            return "Состав сейчас изменить нельзя: бой не открыт.";
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
        if (p == null || request <= 0 || p.hasDisconnected() || p.connection == null
                || p.server.getPlayerList().getPlayer(p.getUUID()) != p) return;
        var requests = connections.get(p.connection);
        if (requests == null) {
            if (action != Action.FETCH) return;
            requests = new Requests(session);
            connections.put(p.connection, requests);
        }
        if (!requests.session.equals(session) || request <= requests.lastRequest) return;
        // Consume the ID even when throttled so replay cannot execute a previously dropped mutation.
        requests.lastRequest = request;
        if (!PacketHandler.allowC2S(p, action == Action.FETCH
                ? PacketHandler.C2SAction.TABLET_SNAPSHOT : PacketHandler.C2SAction.TABLET)) return;

        Result result = Result.OK;
        if (action != Action.FETCH) {
            if (!Objects.equals(match, TeamMatchManager.currentArenaRoster())) result = Result.STALE;
            else if (!denial(p).isEmpty()) result = Result.FORBIDDEN;
            else if (action == Action.JOIN && (team < 0 || team >= TeamId.standardValues().length)) result = Result.INVALID;
            else if (!TeamMatchManager.changeArenaTeam(p, action == Action.LEAVE ? null : TeamId.standardValues()[team])) result = Result.FULL;
        }
        send(p, session, request, result);
    }

    private static void send(ServerPlayer p, UUID session, long request, Result result) {
        boolean arena = MapSetManager.isArenaSet();
        UUID match = arena ? TeamMatchManager.currentArenaRoster() : null;
        if (match == null) {
            String reason = arena ? "Состав появится с началом игры Арены." : "";
            PacketHandler.sendToPlayer(p, new ArenaTeamStatePacket(session, request, NONE, ++revision,
                    arena, -1, reason, result, List.of()));
            return;
        }

        List<ArenaTeamStatePacket.Member> members = new ArrayList<>();
        for (TeamId team : TeamId.standardValues()) for (UUID id : TeamMatchManager.arenaMembers(team)) {
            ServerPlayer member = p.server.getPlayerList().getPlayer(id);
            String name = TeamMatchManager.displayName(p.server, id);
            var life = member == null ? null : ArenaMatchRuntime.life(member);
            String status = member == null ? "не в сети" : life != null && life.rtpUsed && !life.awaitingRespawn
                    && member.getTags().contains("war.playing") && !GameStateManager.isInLobby(member) ? "на карте" : "в лобби/возрождается";
            members.add(new ArenaTeamStatePacket.Member(team.ordinal(), id, name, status));
        }
        TeamId own = TeamMatchManager.getTeam(p);
        PacketHandler.sendToPlayer(p, new ArenaTeamStatePacket(session, request, match, ++revision,
                true, own == null ? -1 : own.ordinal(), denial(p), result, members));
    }

    public static void disconnect(ServerPlayer player) { connections.remove(player.connection); }
    public static void reset() { connections.clear(); }
}
