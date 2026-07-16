package com.makar.tacticaltablet.game.set;

import com.makar.tacticaltablet.game.MapSetManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SetResultService {
    private SetResultService() {
    }

    public static SetLeaderboardSnapshot createSnapshot(
            UUID setId, Map<UUID, String> participants, Collection<GamePerformance> performances) {
        Map<UUID, String> normalizedParticipants = new LinkedHashMap<>();
        if (participants != null) {
            participants.forEach((id, name) -> {
                if (id != null) normalizedParticipants.put(id, name == null || name.isBlank() ? "unknown" : name);
            });
        }
        Map<UUID, String> safeParticipants = Map.copyOf(normalizedParticipants);
        Map<UUID, Map<Integer, GamePerformance>> gamesByPlayer = new HashMap<>();
        if (performances != null) {
            for (GamePerformance performance : performances) {
                if (performance == null || performance.playerId() == null
                        || !safeParticipants.containsKey(performance.playerId())) continue;
                gamesByPlayer.computeIfAbsent(performance.playerId(), ignored -> new LinkedHashMap<>())
                        .putIfAbsent(performance.gameNumber(), performance);
            }
        }

        List<SetPlayerResult> results = new ArrayList<>();
        for (Map.Entry<UUID, String> participant : safeParticipants.entrySet()) {
            Map<Integer, GamePerformance> played = gamesByPlayer.getOrDefault(participant.getKey(), Map.of());
            List<GamePerformance> setGames = new ArrayList<>();
            for (int game = 1; game <= MapSetManager.GAMES_PER_MAP; game++) {
                setGames.add(played.getOrDefault(game,
                        GamePerformance.missed(game, participant.getKey(), participant.getValue())));
            }
            results.add(SetPlayerResult.fromGames(participant.getKey(), participant.getValue(), setGames));
        }
        results.sort(SetScoringRules.SET_RESULT_COMPARATOR);
        return new SetLeaderboardSnapshot(setId, safeParticipants.size(), results);
    }

    public static SetRewardSummary createRewardSummary(SetLeaderboardSnapshot snapshot) {
        return createRewardSummary(snapshot, false);
    }

    public static SetRewardSummary createRewardSummary(
            SetLeaderboardSnapshot snapshot,
            boolean competitiveSet
    ) {
        if (snapshot == null) return null;
        int reward = competitiveSet
                ? 0
                : SetRewardService.calculateSetWinnerReward(snapshot.participantCount());
        int count = Math.min(SetRewardService.rewardedPlaces(snapshot.participantCount()),
                snapshot.orderedResults().size());
        List<SetPlacement> placements = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SetPlayerResult player = snapshot.orderedResults().get(i);
            placements.add(new SetPlacement(i + 1, player.playerId(), player.playerName(), player.totalScore(),
                    player.wins(), player.kills(), player.assists(), player.effectivePvpDamage(), player.deaths()));
        }
        return new SetRewardSummary(snapshot.setId(), snapshot.participantCount(), reward, placements);
    }

    public static List<GamePerformance> flatten(Map<String, List<GamePerformance>> gamesByPlayer) {
        if (gamesByPlayer == null || gamesByPlayer.isEmpty()) return List.of();
        List<GamePerformance> result = new ArrayList<>();
        for (List<GamePerformance> games : gamesByPlayer.values()) {
            if (games != null) result.addAll(games);
        }
        result.sort(Comparator.comparingInt(GamePerformance::gameNumber)
                .thenComparing(GamePerformance::playerId, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(result);
    }
}
