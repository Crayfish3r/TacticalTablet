package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.moderation.ModerModeManager;
import net.minecraft.server.level.ServerPlayer;

public final class ActivePvpParticipant {
    private ActivePvpParticipant() {
    }

    public static boolean isEligible(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return ActivePvpParticipantPolicy.isEligible(
                MatchAdmissionManager.isCurrentMatchParticipant(player.getUUID()),
                player.getTags().contains("war.playing"),
                MatchAdmissionManager.isLateSpectator(player),
                player.isSpectator(),
                ModerModeManager.isInModerMode(player),
                LivesManager.isEliminated(player)
        );
    }
}
