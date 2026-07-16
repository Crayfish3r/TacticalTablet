package com.makar.tacticaltablet.game.set;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompetitiveSetRewardPolicyTest {

    @Test
    void competitiveWinnerPodiumAndRestartRecoveryNeverQualifyForFinalCoins() {
        SetRewardSummary winner = summary(100, List.of(placement(1)));
        SetRewardSummary podium = summary(100, List.of(placement(1), placement(2), placement(3)));

        assertFalse(SetRewardService.shouldAwardFinalCoins(true, winner));
        assertFalse(SetRewardService.shouldAwardFinalCoins(true, podium));
        // The same persisted positive summary can be seen again after a restart or repeated finalization.
        assertFalse(SetRewardService.shouldAwardFinalCoins(true, winner));
    }

    @Test
    void zeroRewardDoesNotQualifyAndCannotCreateAReceipt() {
        assertFalse(SetRewardService.shouldAwardFinalCoins(true, summary(0, List.of(placement(1)))));
        assertFalse(SetRewardService.shouldAwardFinalCoins(false, summary(0, List.of(placement(1)))));
    }

    @Test
    void casualRewardPolicyRemainsUnchanged() {
        assertEquals(35, SetRewardService.calculateSetWinnerReward(6));
        assertTrue(SetRewardService.shouldAwardFinalCoins(false, summary(35, List.of(placement(1)))));
    }

    private static SetRewardSummary summary(int coins, List<SetPlacement> placements) {
        return new SetRewardSummary(UUID.randomUUID(), 6, coins, placements);
    }

    private static SetPlacement placement(int place) {
        return new SetPlacement(place, UUID.randomUUID(), "Player" + place, 10, 1, 2, 3, 4.0D, 5);
    }
}
