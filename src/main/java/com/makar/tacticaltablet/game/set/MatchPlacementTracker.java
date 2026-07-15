package com.makar.tacticaltablet.game.set;

import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.team.TeamId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MatchPlacementTracker {
    private MatchMode mode = MatchMode.SOLO;
    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    private final Map<UUID, Elimination> playerEliminations = new HashMap<>();
    private final Map<TeamId, Elimination> teamEliminations = new EnumMap<>(TeamId.class);
    private long eliminationSequence;

    public void start(MatchMode mode) {
        reset();
        this.mode = mode == null ? MatchMode.SOLO : mode;
    }

    public void register(UUID playerId, String playerName, TeamId teamId) {
        if (playerId == null) return;
        participants.putIfAbsent(playerId, new Participant(playerId, safeName(playerName), teamId));
    }

    public void recordPlayerEliminated(UUID playerId, int serverTick) {
        if (playerId == null || !participants.containsKey(playerId)) return;
        playerEliminations.putIfAbsent(playerId, new Elimination(serverTick, ++eliminationSequence));
    }

    public void recordTeamEliminated(TeamId teamId, int serverTick) {
        if (teamId == null) return;
        teamEliminations.putIfAbsent(teamId, new Elimination(serverTick, ++eliminationSequence));
    }

    public PlacementSnapshot finish(
            Collection<UUID> winnerIds,
            TeamId winningTeam,
            Map<UUID, CombatTotals> combatTotals
    ) {
        Set<UUID> safeWinners = winnerIds == null ? Set.of() : Set.copyOf(winnerIds);
        Map<UUID, CombatTotals> safeCombat = combatTotals == null ? Map.of() : combatTotals;
        Map<UUID, Integer> placements = mode.isTeamMode()
                ? teamPlacements(winningTeam, safeCombat)
                : soloPlacements(safeWinners, safeCombat);
        List<PlayerPlacement> rows = new ArrayList<>();
        for (Participant participant : participants.values()) {
            rows.add(new PlayerPlacement(participant.playerId(), participant.playerName(),
                    placements.getOrDefault(participant.playerId(), 0), participant.teamId()));
        }
        rows.sort(Comparator.comparing(PlayerPlacement::playerId));
        return new PlacementSnapshot(List.copyOf(rows));
    }

    public void reset() {
        mode = MatchMode.SOLO;
        participants.clear();
        playerEliminations.clear();
        teamEliminations.clear();
        eliminationSequence = 0L;
    }

    private Map<UUID, Integer> soloPlacements(Set<UUID> winnerIds, Map<UUID, CombatTotals> combatTotals) {
        List<Participant> ordered = new ArrayList<>(participants.values());
        ordered.sort(Comparator
                .comparing((Participant participant) -> !winnerIds.contains(participant.playerId()))
                .thenComparing((Participant participant) -> playerEliminations.get(participant.playerId()),
                        Comparator.nullsFirst(Comparator.comparingInt(Elimination::serverTick).reversed()
                                .thenComparing(Comparator.comparingLong(Elimination::sequence).reversed())))
                .thenComparing((Participant participant) -> combatTotals.getOrDefault(
                        participant.playerId(), CombatTotals.ZERO), CombatTotals.BEST_FIRST)
                .thenComparing(Participant::playerName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Participant::playerId));
        Map<UUID, Integer> result = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) result.put(ordered.get(i).playerId(), i + 1);
        return result;
    }

    private Map<UUID, Integer> teamPlacements(TeamId winningTeam, Map<UUID, CombatTotals> combatTotals) {
        Set<TeamId> participatingTeams = new LinkedHashSet<>();
        for (Participant participant : participants.values()) {
            if (participant.teamId() != null) participatingTeams.add(participant.teamId());
        }
        List<TeamId> orderedTeams = new ArrayList<>(participatingTeams);
        orderedTeams.sort(Comparator
                .comparing((TeamId team) -> team != winningTeam)
                .thenComparing((TeamId team) -> teamEliminations.get(team),
                        Comparator.nullsFirst(Comparator.comparingInt(Elimination::serverTick).reversed()))
                .thenComparing((TeamId team) -> teamCombat(team, combatTotals), CombatTotals.BEST_FIRST)
                .thenComparingInt(Enum::ordinal));
        Map<TeamId, Integer> teamPlaces = new EnumMap<>(TeamId.class);
        for (int i = 0; i < orderedTeams.size(); i++) teamPlaces.put(orderedTeams.get(i), i + 1);
        Map<UUID, Integer> result = new HashMap<>();
        for (Participant participant : participants.values()) {
            result.put(participant.playerId(), teamPlaces.getOrDefault(participant.teamId(), 0));
        }
        return result;
    }

    private CombatTotals teamCombat(TeamId team, Map<UUID, CombatTotals> combatTotals) {
        int kills = 0;
        int assists = 0;
        double damage = 0.0D;
        for (Participant participant : participants.values()) {
            if (participant.teamId() != team) continue;
            CombatTotals totals = combatTotals.getOrDefault(participant.playerId(), CombatTotals.ZERO);
            kills += totals.kills();
            assists += totals.assists();
            damage += totals.damage();
        }
        return new CombatTotals(kills, assists, damage);
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private record Participant(UUID playerId, String playerName, TeamId teamId) {
    }

    private record Elimination(int serverTick, long sequence) {
    }

    public record CombatTotals(int kills, int assists, double damage) {
        private static final CombatTotals ZERO = new CombatTotals(0, 0, 0.0D);
        private static final Comparator<CombatTotals> BEST_FIRST = Comparator
                .comparingInt((CombatTotals totals) -> SetScoringRules.getKillPoints(totals.kills())
                        + SetScoringRules.getAssistPoints(totals.assists())
                        + SetScoringRules.getDamagePoints(totals.damage())).reversed()
                .thenComparing(Comparator.comparingInt(CombatTotals::kills).reversed())
                .thenComparing(Comparator.comparingDouble(CombatTotals::damage).reversed());
    }

    public record PlayerPlacement(UUID playerId, String playerName, int placement, TeamId teamId) {
    }

    public record PlacementSnapshot(List<PlayerPlacement> players) {
        public PlacementSnapshot {
            players = players == null ? List.of() : List.copyOf(players);
        }
    }
}
