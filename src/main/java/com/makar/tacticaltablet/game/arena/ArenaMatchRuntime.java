package com.makar.tacticaltablet.game.arena;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.*;
import com.makar.tacticaltablet.game.lifecycle.MatchState;
import com.makar.tacticaltablet.game.npc.NpcEncounterManager;
import com.makar.tacticaltablet.game.set.GamePerformance;
import com.makar.tacticaltablet.tablet.net.ArenaStatePacket;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.storage.LevelResource;

import java.util.*;

/** Owned by the existing coordinator's matchId. No independent tick listener or active-match recovery. */
public final class ArenaMatchRuntime {
    public static final int DURATION_SECONDS = 900;
    public static final int MAX_PARTICIPANTS = 1024;
    private static ArenaScoringRules preparedRules;
    private static Session current;
    private static ArenaSnapshot retainedResult = ArenaSnapshot.EMPTY;
    private ArenaMatchRuntime() { }

    public static String prepare(MinecraftServer server) {
        try {
            preparedRules = ArenaScoringRules.load(server.getWorldPath(LevelResource.ROOT).resolve("tacticaltablet_arena.json"));
            return NpcEncounterManager.prepareArena(server);
        } catch (Exception | LinkageError error) {
            preparedRules = null;
            return "Не удалось загрузить правила Арены: " + error.getMessage();
        }
    }

    public static void start(MinecraftServer server) {
        if (!MapSetManager.isArenaSet()) { clear(); return; }
        if (preparedRules == null) throw new IllegalStateException("Arena preflight was not completed");
        UUID id = GameStateManager.getLifecycleSnapshot().matchId().orElseThrow();
        retainedResult = ArenaSnapshot.EMPTY;
        current = new Session(server, id, preparedRules, server.getTickCount(), MapSetManager.getCurrentGameNumber());
        for (UUID participant : GameStateManager.getLifecycleSnapshot().participantIds()) {
            var player = server.getPlayerList().getPlayer(participant);
            if (player != null) register(player);
        }
        tick(server);
    }

    public static boolean owns(UUID playerId) {
        return current != null && GameStateManager.getLifecycleSnapshot().matchId().filter(current.id::equals).isPresent()
                && current.players.containsKey(playerId);
    }
    public static Life life(ServerPlayer player) {
        return player != null && owns(player.getUUID()) ? current.players.get(player.getUUID()).life : null;
    }
    public static void register(ServerPlayer player) {
        if (current == null || !GameStateManager.getLifecycleSnapshot().participantIds().contains(player.getUUID())) return;
        current.players.computeIfAbsent(player.getUUID(), ignored -> new Totals(player.getGameProfile().getName()));
    }
    public static boolean combatOpen() {
        var s = current;
        if (s == null || s.finished) return false;
        var snapshot = GameStateManager.getLifecycleSnapshot();
        return snapshot.state() == MatchState.RUNNING && snapshot.matchId().filter(s.id::equals).isPresent()
                && GameStateManager.getMatchPhase() == MatchPhase.RUNNING
                && Integer.toUnsignedLong(s.server.getTickCount() - s.startTick) < DURATION_SECONDS * 20L;
    }
    public static boolean accepts(ServerPlayer player) {
        return player != null && combatOpen() && owns(player.getUUID())
                && current.server == player.server && player.server.getPlayerList().getPlayer(player.getUUID()) == player
                && !player.hasDisconnected();
    }
    public static boolean claimDeath(ServerPlayer player) {
        if (!accepts(player)) return false;
        var totals = current.players.get(player.getUUID());
        if (totals.lastDeathEntity == player) return false;
        totals.lastDeathEntity = player;
        totals.deaths++;
        totals.life = new Life();
        totals.life.awaitingRespawn = true;
        return true;
    }
    public static void kill(ServerPlayer player) { if (accepts(player)) current.players.get(player.getUUID()).kills++; }
    public static void assist(UUID playerId) {
        if (combatOpen() && owns(playerId)) current.players.get(playerId).assists++;
    }
    public static void damage(ServerPlayer attacker, double effectiveDamage) {
        if (accepts(attacker) && Double.isFinite(effectiveDamage) && effectiveDamage > 0)
            current.players.get(attacker.getUUID()).damage += effectiveDamage;
    }
    public static void npcKill(ServerPlayer killer, UUID matchId) {
        if (accepts(killer) && current.id.equals(matchId)) current.players.get(killer.getUUID()).npcKills++;
    }

    public static void tick(MinecraftServer server) {
        var s = current;
        if (s == null || s.server != server) return;
        if (!s.finished && Integer.toUnsignedLong(server.getTickCount() - s.startTick) >= DURATION_SECONDS * 20L) {
            s.finished = true; // Admission closes before any result or lifecycle side effects.
            s.finalRows = rows(s);
            NpcEncounterManager.deactivate();
            s.bar.removeAllPlayers(); s.bar.setVisible(false);
        }
        if (!s.finished) {
            int left = secondsLeft();
            s.bar.setName(Component.translatable("screen.tacticaltablet.arena.timer",
                    String.format(Locale.ROOT, "%02d:%02d", left / 60, left % 60), s.gameNumber, 3));
            s.bar.setProgress(left / (float) DURATION_SECONDS);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (owns(p.getUUID())) s.bar.addPlayer(p); else s.bar.removePlayer(p);
            }
        }
        if (s.finished && !s.committed) return; // Final publication follows the durable set commit.
        var state = snapshot();
        var byId = state.rows().stream().collect(java.util.stream.Collectors.toMap(ArenaSnapshot.Row::playerId, r -> r));
        var packet = new ArenaStatePacket(state);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) PacketHandler.sendToPlayer(player,
                state.active() ? new ArenaStatePacket(forViewer(state, byId.get(player.getUUID()))) : packet);
    }

    public static boolean finished() { return current != null && current.finished; }
    public static UUID matchId() { return current == null ? null : current.id; }
    public static List<UUID> leaders() {
        var rows = current == null ? retainedResult.rows() : current.finished ? current.finalRows : rows(current);
        return rows.isEmpty() ? List.of() : rows.stream().filter(r -> r.score() == rows.get(0).score())
                .map(ArenaSnapshot.Row::playerId).toList();
    }
    public static String winnerLabel() {
        var leaders = leaders();
        if (leaders.isEmpty()) return "Нет участников";
        var scores = current == null ? retainedResult.rows() : current.finished ? current.finalRows : rows(current);
        String names = scores.stream().filter(r -> leaders.contains(r.playerId()))
                .map(ArenaSnapshot.Row::name).collect(java.util.stream.Collectors.joining(", "));
        return leaders.size() == 1 ? names : "Ничья: " + names;
    }
    public static List<GamePerformance> performances() {
        if (!finished()) return List.of();
        var s = current; var leaders = leaders();
        return s.finalRows.stream().map(row -> new GamePerformance(s.gameNumber, row.playerId(), row.name(),
                0, row.pvpKills(), row.assists(), s.players.get(row.playerId()).damage, row.deaths(), "",
                true, row.score(), row.npcKills(), leaders.size() == 1 && leaders.contains(row.playerId()))).toList();
    }
    public static ArenaSnapshot snapshot() {
        var s = current;
        if (s != null && s.committed && MapSetManager.isSetComplete() && MapSetManager.getLeaderboardSnapshot() != null) {
            var results = MapSetManager.getLeaderboardSnapshot().orderedResults().stream().map(r -> new ArenaSnapshot.Row(
                    r.playerId(), r.playerName(), r.totalScore(), r.kills(), r.npcKills(), r.assists(), r.deaths())).toList();
            return new ArenaSnapshot(s.id, false, true, 0, s.gameNumber, 3, results, true);
        }
        return s == null ? retainedResult : new ArenaSnapshot(s.id, !s.finished, s.finished && s.committed,
                secondsLeft(), s.gameNumber, 3, s.finished ? s.finalRows : rows(s));
    }
    private static List<ArenaSnapshot.Row> rows(Session s) {
        return s.players.entrySet().stream().map(e -> {
            var t = e.getValue();
            return new ArenaSnapshot.Row(e.getKey(), t.name, s.rules.score(t.kills, t.assists, t.damage, t.npcKills),
                    t.kills, t.npcKills, t.assists, t.deaths);
        }).sorted(Comparator.comparingInt(ArenaSnapshot.Row::score).reversed()
                .thenComparing(ArenaSnapshot.Row::name).thenComparing(ArenaSnapshot.Row::playerId)).toList();
    }
    private static int secondsLeft() {
        var s = current;
        return s == null || s.finished ? 0 : (int) Math.max(0, DURATION_SECONDS
                - Integer.toUnsignedLong(s.server.getTickCount() - s.startTick) / 20);
    }
    public static void sync(ServerPlayer player) {
        var state = snapshot();
        var own = state.rows().stream().filter(r -> r.playerId().equals(player.getUUID())).findFirst().orElse(null);
        PacketHandler.sendToPlayer(player, new ArenaStatePacket(forViewer(state, own)));
    }
    private static ArenaSnapshot forViewer(ArenaSnapshot state, ArenaSnapshot.Row own) {
        if (!state.active()) return state;
        var visible = new ArrayList<>(state.rows().stream().limit(5).toList());
        if (own != null && visible.stream().noneMatch(r -> r.playerId().equals(own.playerId()))) visible.add(own);
        return new ArenaSnapshot(state.matchId(), true, false, state.secondsLeft(), state.gameNumber(), 3, visible);
    }
    /** Cleanup keeps the final immutable table client-side until the next game. */
    public static void clear() {
        retainedResult = current != null && current.committed ? snapshot() : retainedResult;
        if (current != null) { current.bar.removeAllPlayers(); current.bar.setVisible(false); }
        current = null; preparedRules = null;
    }
    public static void published(MinecraftServer server) {
        if (current == null) return;
        current.committed = true;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }
    public static void abort(MinecraftServer server) {
        clear(); retainedResult = ArenaSnapshot.EMPTY;
        if (server != null) for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }
    public static final class Life {
        public boolean kitUsed, rtpUsed, awaitingRespawn;
        public String selectedClass = "";
    }
    private static final class Totals {
        final String name;
        int kills, assists, npcKills, deaths;
        // Server-thread identity of the previous life. Vanilla can retain entity IDs across respawns.
        ServerPlayer lastDeathEntity;
        double damage;
        Life life = new Life();
        Totals(String name) { this.name = name; }
    }
    private static final class Session {
        final MinecraftServer server;
        final UUID id;
        final ArenaScoringRules rules;
        final int startTick, gameNumber;
        final Map<UUID, Totals> players = new HashMap<>();
        final ServerBossEvent bar = new ServerBossEvent(Component.literal("Арена — 15:00"),
                BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        boolean finished, committed;
        List<ArenaSnapshot.Row> finalRows = List.of();
        Session(MinecraftServer server, UUID id, ArenaScoringRules rules, int startTick, int gameNumber) {
            this.server = server; this.id = id; this.rules = rules; this.startTick = startTick; this.gameNumber = gameNumber;
        }
    }
}
