package com.makar.tacticaltablet.game.set;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Set;

public final class SetRewardPresentation {
    public static final int GOLD = 0xFFD700;
    public static final int SILVER = 0xC0C0C0;
    public static final int BRONZE = 0xCD7F32;

    private SetRewardPresentation() { }

    public static Component title(SetRewardSummary summary) {
        return Component.literal(summary != null && summary.placements().size() > 1
                ? "ПРИЗЁРЫ СЕТА" : "ПОБЕДИТЕЛЬ СЕТА");
    }

    public static Component subtitle(SetRewardSummary summary) {
        MutableComponent result = Component.empty();
        if (summary == null || summary.placements().isEmpty()) return Component.literal("Награда не назначена");
        for (int i = 0; i < summary.placements().size(); i++) {
            if (i > 0) result.append(Component.literal("   "));
            SetPlacement placement = summary.placements().get(i);
            result.append(Component.literal(placement.place() + ". " + placement.playerName())
                    .withStyle(style -> style.withColor(color(placement.place()))));
        }
        return result;
    }

    public static List<Component> chat(SetRewardSummary summary) {
        return chat(summary, summary == null ? Set.of() : summary.placements().stream()
                .map(SetPlacement::place).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    public static List<Component> chat(SetRewardSummary summary, Set<Integer> successfullyPersistedPlaces) {
        if (summary == null || summary.placements().isEmpty()
                || successfullyPersistedPlaces == null || successfullyPersistedPlaces.isEmpty()) return List.of();
        List<SetPlacement> paid = summary.placements().stream()
                .filter(placement -> successfullyPersistedPlaces.contains(placement.place()))
                .toList();
        if (paid.isEmpty()) return List.of();
        if (summary.placements().size() == 1 && paid.size() == 1) {
            SetPlacement winner = paid.get(0);
            return List.of(Component.literal("[WAR] Победитель сета: ")
                    .append(Component.literal(winner.playerName()).withStyle(style -> style.withColor(GOLD)))
                    .append(Component.literal(". Награда: " + summary.rewardCoins() + " coins. Участников: "
                            + summary.participantCount() + ".")));
        }
        java.util.ArrayList<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.literal("[WAR] Призёры сета:"));
        for (SetPlacement placement : paid) {
            lines.add(Component.literal(placement.place() + ". ")
                    .append(Component.literal(placement.playerName()).withStyle(style -> style.withColor(color(placement.place()))))
                    .append(Component.literal(" — " + summary.rewardCoins() + " coins")));
        }
        lines.add(Component.literal("Участников сета: " + summary.participantCount()));
        return List.copyOf(lines);
    }

    private static int color(int place) {
        return place == 1 ? GOLD : place == 2 ? SILVER : BRONZE;
    }
}
