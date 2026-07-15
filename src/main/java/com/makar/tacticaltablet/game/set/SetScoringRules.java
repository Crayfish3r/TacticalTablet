package com.makar.tacticaltablet.game.set;

import java.util.Comparator;

public final class SetScoringRules {
    public static final int ASSIST_WINDOW_TICKS = 15 * 20;
    private static final int KILL_POINTS = 2;
    private static final int ASSIST_POINTS = 1;
    private static final double DAMAGE_PER_POINT = 20.0D;

    public static final Comparator<SetPlayerResult> SET_RESULT_COMPARATOR = Comparator
            .comparingInt(SetPlayerResult::totalScore).reversed()
            .thenComparing(Comparator.comparingInt(SetPlayerResult::wins).reversed())
            .thenComparing(Comparator.comparingInt(SetPlayerResult::kills).reversed())
            .thenComparing(Comparator.comparingDouble(SetPlayerResult::effectivePvpDamage).reversed())
            .thenComparingInt(SetPlayerResult::deaths)
            .thenComparing(SetPlayerResult::playerName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(SetPlayerResult::playerId, Comparator.nullsLast(Comparator.naturalOrder()));

    public static final Comparator<GamePerformance> GAME_PERFORMANCE_COMPARATOR = Comparator
            .comparingInt(SetScoringRules::calculateGameScore).reversed()
            .thenComparingInt(game -> game.placement() <= 0 ? Integer.MAX_VALUE : game.placement())
            .thenComparing(Comparator.comparingInt(GamePerformance::kills).reversed())
            .thenComparing(Comparator.comparingInt(GamePerformance::assists).reversed())
            .thenComparing(Comparator.comparingDouble(GamePerformance::effectivePvpDamage).reversed())
            .thenComparingInt(GamePerformance::deaths)
            .thenComparing(GamePerformance::playerName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(GamePerformance::playerId, Comparator.nullsLast(Comparator.naturalOrder()));

    private SetScoringRules() {
    }

    public static int getPlacementPoints(int placement) {
        return switch (placement) {
            case 1 -> 12;
            case 2 -> 9;
            case 3 -> 7;
            case 4 -> 5;
            case 5 -> 4;
            case 6 -> 3;
            case 7 -> 2;
            default -> placement >= 8 ? 1 : 0;
        };
    }

    public static int getKillPoints(int kills) {
        return Math.max(0, kills) * KILL_POINTS;
    }

    public static int getAssistPoints(int assists) {
        return Math.max(0, assists) * ASSIST_POINTS;
    }

    public static int getDamagePoints(double effectivePvpDamage) {
        if (!Double.isFinite(effectivePvpDamage) || effectivePvpDamage <= 0.0D) return 0;
        return (int) Math.floor(effectivePvpDamage / DAMAGE_PER_POINT);
    }

    public static int calculateGameScore(GamePerformance performance) {
        if (performance == null) return 0;
        return getPlacementPoints(performance.placement())
                + getKillPoints(performance.kills())
                + getAssistPoints(performance.assists())
                + getDamagePoints(performance.effectivePvpDamage());
    }

    public static ScoreBreakdown breakdown(SetPlayerResult result) {
        if (result == null) return new ScoreBreakdown(0, 0, 0, 0, 0);
        int placement = result.games().stream().mapToInt(game -> getPlacementPoints(game.placement())).sum();
        int kills = result.games().stream().mapToInt(game -> getKillPoints(game.kills())).sum();
        int assists = result.games().stream().mapToInt(game -> getAssistPoints(game.assists())).sum();
        int damage = result.games().stream().mapToInt(game -> getDamagePoints(game.effectivePvpDamage())).sum();
        return new ScoreBreakdown(placement, kills, assists, damage, placement + kills + assists + damage);
    }

    public record ScoreBreakdown(int placementPoints, int killPoints, int assistPoints, int damagePoints, int total) {
    }
}
