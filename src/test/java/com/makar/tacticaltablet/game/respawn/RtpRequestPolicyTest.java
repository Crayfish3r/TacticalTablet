package com.makar.tacticaltablet.game.respawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtpRequestPolicyTest {

    @Test
    void runningParticipantInLobbyIsReadyRegardlessOfStartupPlayerMinimum() {
        assertEquals(RtpTimerManager.RtpEligibilityResult.READY,
                RtpTimerManager.classifyEligibility(true, true, true, true,
                        false, true, true));
    }

    @Test
    void incompleteLobbyInitializationIsRetryableInsteadOfLost() {
        assertEquals(RtpTimerManager.RtpEligibilityResult.RETRYABLE,
                RtpTimerManager.classifyEligibility(true, true, true, true,
                        false, false, true));
        assertEquals(RtpTimerManager.RtpEligibilityResult.RETRYABLE,
                RtpTimerManager.classifyEligibility(true, true, true, true,
                        false, true, false));
    }

    @Test
    void permanentConditionsCancelTheRequest() {
        assertEquals(RtpTimerManager.RtpEligibilityResult.CANCELLED,
                RtpTimerManager.classifyEligibility(false, true, true, true,
                        false, true, true));
        assertEquals(RtpTimerManager.RtpEligibilityResult.CANCELLED,
                RtpTimerManager.classifyEligibility(true, false, true, true,
                        false, true, true));
        assertEquals(RtpTimerManager.RtpEligibilityResult.CANCELLED,
                RtpTimerManager.classifyEligibility(true, true, false, true,
                        false, true, true));
        assertEquals(RtpTimerManager.RtpEligibilityResult.CANCELLED,
                RtpTimerManager.classifyEligibility(true, true, true, false,
                        false, true, true));
        assertEquals(RtpTimerManager.RtpEligibilityResult.CANCELLED,
                RtpTimerManager.classifyEligibility(true, true, true, true,
                        true, true, true));
    }

    @Test
    void lateJoinTeamSelectionExcludesAlreadyPlayingMembers() {
        assertTrue(RtpTimerManager.isPendingRtpParticipant(true, true, true, false, false));
        assertFalse(RtpTimerManager.isPendingRtpParticipant(true, false, false, true, false));
        assertFalse(RtpTimerManager.isPendingRtpParticipant(true, true, true, true, false));
        assertFalse(RtpTimerManager.isPendingRtpParticipant(false, true, true, false, false));
        assertFalse(RtpTimerManager.isPendingRtpParticipant(true, true, true, false, true));
    }

    @Test
    void twoLateJoinersCanBePendingWhileExistingTeammatesStayExcluded() {
        boolean firstLateJoin = RtpTimerManager.isPendingRtpParticipant(true, true, true, false, false);
        boolean secondLateJoin = RtpTimerManager.isPendingRtpParticipant(true, true, true, false, false);
        boolean existingTeammate = RtpTimerManager.isPendingRtpParticipant(true, false, false, true, false);

        assertTrue(firstLateJoin);
        assertTrue(secondLateJoin);
        assertFalse(existingTeammate);
    }
}
