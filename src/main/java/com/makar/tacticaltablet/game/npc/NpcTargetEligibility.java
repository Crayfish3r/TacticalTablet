package com.makar.tacticaltablet.game.npc;

import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchState;

import java.util.UUID;

/** Pure decision used by the dynamic API callback; no tags alone can admit a player. */
final class NpcTargetEligibility {
    static boolean canAttack(UUID matchId, MatchLifecycleSnapshot snapshot, UUID playerId,
                             boolean combatRunning, boolean bound, boolean aliveParticipant,
                             boolean aliveEntity, boolean inArena, boolean spectator, boolean creative,
                             boolean moderator, boolean playing) {
        return matchId != null && snapshot.state() == MatchState.RUNNING && combatRunning
                && snapshot.matchId().filter(matchId::equals).isPresent()
                && snapshot.participantIds().contains(playerId) && bound && aliveParticipant && aliveEntity
                && inArena && !spectator && !creative && !moderator && playing;
    }
}
