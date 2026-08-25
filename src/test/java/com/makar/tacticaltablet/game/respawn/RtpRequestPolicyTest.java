package com.makar.tacticaltablet.game.respawn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtpRequestPolicyTest {

    @Test
    void requestCanBeScheduledDuringControlledStartTransition() {
        assertTrue(RtpTimerManager.canStartRequest(false, true));
        assertTrue(RtpTimerManager.canStartRequest(true, false));
        assertFalse(RtpTimerManager.canStartRequest(false, false));
    }
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

    @Test
    void chaosTeamWaitsUntilEveryPendingLobbyMemberHasSelectedAndReceivedKit() {
        boolean ready = RtpTimerManager.isChaosDeploymentReady(true, false, true);
        boolean unselected = RtpTimerManager.isChaosDeploymentReady(true, true, false);
        boolean selectedWithoutKit = RtpTimerManager.isChaosDeploymentReady(true, false, false);

        assertTrue(RtpTimerManager.shouldPostponeTeamRtp(true, List.of(ready, unselected)));
        assertTrue(RtpTimerManager.shouldPostponeTeamRtp(true, List.of(ready, selectedWithoutKit)));
        assertFalse(RtpTimerManager.shouldPostponeTeamRtp(true, List.of(ready, ready)));
    }

    @Test
    void fightingAndEliminatedTeammatesDoNotJoinOrBlockLaterDeployment() {
        boolean waitingReadyPlayer = RtpTimerManager.isPendingRtpParticipant(true, true, true, false, false);
        boolean alreadyFighting = RtpTimerManager.isPendingRtpParticipant(true, false, false, true, false);
        boolean eliminated = RtpTimerManager.isPendingRtpParticipant(false, false, false, false, true);

        assertTrue(waitingReadyPlayer);
        assertFalse(alreadyFighting);
        assertFalse(eliminated);
        assertFalse(RtpTimerManager.shouldPostponeTeamRtp(true, List.of(
                RtpTimerManager.isChaosDeploymentReady(true, false, true))));
    }

    @Test
    void nonChaosTeamDeploymentRemainsUnchanged() {
        assertTrue(RtpTimerManager.isChaosDeploymentReady(false, true, false));
        assertFalse(RtpTimerManager.shouldPostponeTeamRtp(false, List.of(false, false)));
    }
}
