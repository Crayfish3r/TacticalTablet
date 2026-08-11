package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.net.ContractSelectionStatePacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractClientStateTimerTest {

    @Test
    void timerCorrectionDoesNotReplaceAuthoritativeSelectionData() {
        ContractSelectionStatePacket.TargetEntry target = new ContractSelectionStatePacket.TargetEntry(
                UUID.randomUUID(), "Target", "scout", 2, 3, 40, 1, 5, 20
        );
        ContractClientState.updateSelection(true, 30, 60_000L, true, true, List.of(target));
        long revision = ContractClientState.revision();

        ContractClientState.updateSelectionTimer(false, -5);

        assertFalse(ContractClientState.isSelectionActive());
        assertEquals(0, ContractClientState.getSelectionSecondsLeft());
        assertTrue(ContractClientState.hasActiveContract());
        assertTrue(ContractClientState.isSoloMode());
        assertEquals(List.of(target), ContractClientState.getTargets());
        assertTrue(ContractClientState.getCooldownLeftMs() > 0L);
        assertEquals(revision, ContractClientState.revision());
    }
}
