package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressUnloadPolicyTest {
    @Test
    void pendingOrFailedLogoutWriteKeepsProfileAuthoritative() {
        assertFalse(ProgressUnloadPolicy.canEvict(false, true, 12L, 11L));
        assertFalse(ProgressUnloadPolicy.canEvict(false, true, 12L, 12L));
        assertFalse(ProgressUnloadPolicy.canEvict(false, false, 12L, 11L));
        assertFalse(ProgressUnloadPolicy.canEvict(false, false, null, 12L));
    }

    @Test
    void reconnectCancelsEvictionEvenAfterWriteCompletes() {
        assertFalse(ProgressUnloadPolicy.canEvict(true, false, 12L, 12L));
    }

    @Test
    void completedLatestRevisionAllowsOfflineEviction() {
        assertTrue(ProgressUnloadPolicy.canEvict(false, false, 12L, 12L));
        assertTrue(ProgressUnloadPolicy.canEvict(false, false, 12L, 13L));
    }
}
