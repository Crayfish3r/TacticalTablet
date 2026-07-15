package com.makar.tacticaltablet.game.set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetRewardCountdownTest {
    @Test
    void mapVoteTransitionIsIssuedExactlyOnceAfterFifteenSeconds() {
        SetRewardCountdown countdown = new SetRewardCountdown();
        countdown.resume(15);

        for (int second = 1; second <= 14; second++) assertFalse(countdown.tickSecond());
        assertTrue(countdown.tickSecond());
        assertFalse(countdown.tickSecond());
        assertFalse(countdown.tickSecond());
    }
}
