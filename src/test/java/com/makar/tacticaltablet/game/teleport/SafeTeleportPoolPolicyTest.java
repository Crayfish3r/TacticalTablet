package com.makar.tacticaltablet.game.teleport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeTeleportPoolPolicyTest {

    @Test
    void poolRefillsBelowLowWaterMarkAndStopsAtTarget() {
        assertTrue(SafeTeleport.shouldStartRefill(11, 32, 0));
        assertFalse(SafeTeleport.shouldStartRefill(12, 32, 0));
        assertFalse(SafeTeleport.shouldStartRefill(31, 32, 0));
        assertFalse(SafeTeleport.shouldStartRefill(0, 0, 0));
    }

    @Test
    void refillCooldownPreventsImmediateRetryAfterExhaustedSearch() {
        assertFalse(SafeTeleport.shouldStartRefill(0, 32, 1));
        assertTrue(SafeTeleport.shouldStartRefill(0, 32, 0));
    }

    @Test
    void unavailableChunkIsPreservedButUnsafePointIsPruned() {
        assertFalse(SafeTeleport.shouldPrune(SafeTeleport.SpawnValidationResult.SAFE));
        assertFalse(SafeTeleport.shouldPrune(SafeTeleport.SpawnValidationResult.CHUNK_UNAVAILABLE));
        assertTrue(SafeTeleport.shouldPrune(SafeTeleport.SpawnValidationResult.UNSAFE));
    }
}
