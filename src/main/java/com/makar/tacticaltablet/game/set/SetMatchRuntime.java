package com.makar.tacticaltablet.game.set;

import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.team.TeamId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SetMatchRuntime {
    private static final CombatAssistTracker ASSISTS = new CombatAssistTracker();
    private static final MatchPlacementTracker PLACEMENTS = new MatchPlacementTracker();

    private SetMatchRuntime() {
    }

    public static void startMatch(MatchMode mode) {
        ASSISTS.reset();
        PLACEMENTS.start(mode);
    }

    public static void registerParticipant(UUID playerId, String playerName, TeamId teamId) {
        PLACEMENTS.register(playerId, playerName, teamId);
    }

    public static void recordEffectivePvpDamage(
            UUID attackerId, String attackerName, UUID victimId, int serverTick) {
        ASSISTS.recordEffectivePvpDamage(attackerId, attackerName, victimId, serverTick);
    }

    public static boolean claimDeath(UUID victimId, int serverTick) {
        return ASSISTS.claimDeath(victimId, serverTick);
    }

    public static List<CombatAssistTracker.AssistCredit> resolveAssists(
            UUID victimId, UUID killerId, int serverTick) {
        return ASSISTS.resolveForConfirmedPvpKill(victimId, killerId, serverTick);
    }

    public static void clearVictimAttribution(UUID victimId) {
        ASSISTS.clearVictim(victimId);
    }

    public static void recordPlayerEliminated(UUID playerId, int serverTick) {
        PLACEMENTS.recordPlayerEliminated(playerId, serverTick);
    }

    public static void recordTeamEliminated(TeamId teamId, int serverTick) {
        PLACEMENTS.recordTeamEliminated(teamId, serverTick);
    }

    public static MatchPlacementTracker.PlacementSnapshot finishMatch(
            Collection<UUID> winnerIds,
            TeamId winningTeam,
            Map<UUID, MatchPlacementTracker.CombatTotals> combatTotals) {
        return PLACEMENTS.finish(winnerIds, winningTeam, combatTotals);
    }

    public static void reset() {
        ASSISTS.reset();
        PLACEMENTS.reset();
    }
}
