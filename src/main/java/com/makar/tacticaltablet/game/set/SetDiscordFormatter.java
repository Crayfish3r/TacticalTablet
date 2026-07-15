package com.makar.tacticaltablet.game.set;

import java.util.ArrayList;
import java.util.List;

public final class SetDiscordFormatter {
    private SetDiscordFormatter() {
    }

    public static String formatLeaderboard(SetLeaderboardSnapshot snapshot, int maxCharacters) {
        return formatLeaderboardPages(snapshot, maxCharacters).get(0);
    }

    public static List<String> formatLeaderboardPages(SetLeaderboardSnapshot snapshot, int maxCharacters) {
        int limit = Math.max(256, Math.min(4000, maxCharacters));
        List<String> pages = new ArrayList<>();
        StringBuilder result = new StringBuilder("\n**Итоговая таблица**\n");
        if (snapshot == null || snapshot.orderedResults().isEmpty()) {
            return List.of(result.append("Нет данных по сету.").toString());
        }
        for (int i = 0; i < snapshot.orderedResults().size(); i++) {
            SetPlayerResult player = snapshot.orderedResults().get(i);
            String row = (i + 1) + ". " + player.playerName()
                    + " | " + player.totalScore() + " очк."
                    + " | W:" + player.wins()
                    + " | K:" + player.kills()
                    + " | A:" + player.assists()
                    + " | DMG:" + formatDamage(player.effectivePvpDamage())
                    + " | D:" + player.deaths() + "\n";
            if (result.length() + row.length() > limit) {
                pages.add(result.toString());
                result = new StringBuilder("**Итоговая таблица (продолжение)**\n");
            }
            result.append(row);
        }
        String breakdownHeader = "\n**Разбивка очков podium**\n";
        if (result.length() + breakdownHeader.length() > limit) {
            pages.add(result.toString());
            result = new StringBuilder();
        }
        result.append(breakdownHeader);
        for (int i = 0; i < Math.min(3, snapshot.orderedResults().size()); i++) {
            SetPlayerResult player = snapshot.orderedResults().get(i);
            SetScoringRules.ScoreBreakdown breakdown = SetScoringRules.breakdown(player);
            String row = (i + 1) + ". " + player.playerName()
                    + " — Placement:" + breakdown.placementPoints()
                    + " Kills:" + breakdown.killPoints()
                    + " Assists:" + breakdown.assistPoints()
                    + " Damage:" + breakdown.damagePoints()
                    + " Total:" + breakdown.total() + "\n";
            if (result.length() + row.length() > limit) {
                pages.add(result.toString());
                result = new StringBuilder("**Разбивка очков podium (продолжение)**\n");
            }
            result.append(row);
        }
        pages.add(result.toString());
        return List.copyOf(pages);
    }

    private static String formatDamage(double damage) {
        return String.format(java.util.Locale.US, "%.1f", Math.max(0.0D, damage));
    }
}
