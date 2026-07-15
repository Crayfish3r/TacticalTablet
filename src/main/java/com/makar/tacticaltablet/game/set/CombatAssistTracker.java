package com.makar.tacticaltablet.game.set;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CombatAssistTracker {
    private final Map<UUID, Map<UUID, Hit>> hitsByVictim = new HashMap<>();
    private final Map<UUID, Integer> lastProcessedDeathTick = new HashMap<>();

    public void recordEffectivePvpDamage(UUID attackerId, String attackerName, UUID victimId, int serverTick) {
        if (attackerId == null || victimId == null || attackerId.equals(victimId)) return;
        hitsByVictim.computeIfAbsent(victimId, ignored -> new HashMap<>())
                .put(attackerId, new Hit(attackerId, safeName(attackerName), serverTick));
    }

    public boolean claimDeath(UUID victimId, int serverTick) {
        if (victimId == null) return false;
        Integer previous = lastProcessedDeathTick.put(victimId, serverTick);
        return previous == null || previous != serverTick;
    }

    public List<AssistCredit> resolveForConfirmedPvpKill(UUID victimId, UUID killerId, int serverTick) {
        Map<UUID, Hit> hits = hitsByVictim.remove(victimId);
        if (hits == null || hits.isEmpty()) return List.of();
        List<AssistCredit> result = new ArrayList<>();
        for (Hit hit : hits.values()) {
            int age = serverTick - hit.lastEffectiveDamageTick();
            if (hit.attackerId().equals(killerId) || age < 0 || age > SetScoringRules.ASSIST_WINDOW_TICKS) continue;
            result.add(new AssistCredit(hit.attackerId(), hit.attackerName()));
        }
        result.sort(java.util.Comparator.comparing(AssistCredit::playerId));
        return List.copyOf(result);
    }

    public void clearVictim(UUID victimId) {
        if (victimId != null) hitsByVictim.remove(victimId);
    }

    public void reset() {
        hitsByVictim.clear();
        lastProcessedDeathTick.clear();
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private record Hit(UUID attackerId, String attackerName, int lastEffectiveDamageTick) {
    }

    public record AssistCredit(UUID playerId, String playerName) {
    }
}
