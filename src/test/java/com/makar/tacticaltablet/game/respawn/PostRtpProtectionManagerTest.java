package com.makar.tacticaltablet.game.respawn;

import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostRtpProtectionManagerTest {

    @AfterEach
    void clearState() {
        PostRtpProtectionManager.clearAll();
    }

    @Test
    void grantUsesAbsoluteServerTickExpiry() {
        UUID playerId = UUID.randomUUID();

        PostRtpProtectionManager.grantAtTick(playerId, 100L, 140);

        assertTrue(PostRtpProtectionManager.isProtectedAtTick(playerId, 100L));
        assertEquals(140, PostRtpProtectionManager.remainingTicksAtTick(playerId, 100L));
    }

    @Test
    void expiryIsExclusiveAndRemovesStateLazily() {
        UUID playerId = UUID.randomUUID();
        PostRtpProtectionManager.grantAtTick(playerId, 100L, 140);

        assertTrue(PostRtpProtectionManager.isProtectedAtTick(playerId, 239L));
        assertEquals(1, PostRtpProtectionManager.remainingTicksAtTick(playerId, 239L));
        assertFalse(PostRtpProtectionManager.isProtectedAtTick(playerId, 240L));
        assertEquals(0, PostRtpProtectionManager.remainingTicksAtTick(playerId, 240L));
    }

    @Test
    void repeatedGrantExtendsButNeverShortensProtection() {
        UUID extended = UUID.randomUUID();
        PostRtpProtectionManager.grantAtTick(extended, 100L, 100);
        PostRtpProtectionManager.grantAtTick(extended, 150L, 100);
        assertEquals(100, PostRtpProtectionManager.remainingTicksAtTick(extended, 150L));

        UUID preserved = UUID.randomUUID();
        PostRtpProtectionManager.grantAtTick(preserved, 100L, 200);
        PostRtpProtectionManager.grantAtTick(preserved, 150L, 100);
        assertEquals(150, PostRtpProtectionManager.remainingTicksAtTick(preserved, 150L));
    }

    @Test
    void clearAndClearAllRemoveProtection() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PostRtpProtectionManager.grantAtTick(first, 10L, 100);
        PostRtpProtectionManager.grantAtTick(second, 10L, 100);

        PostRtpProtectionManager.clear(first);
        assertFalse(PostRtpProtectionManager.isProtectedAtTick(first, 10L));
        assertTrue(PostRtpProtectionManager.isProtectedAtTick(second, 10L));

        PostRtpProtectionManager.clearAll();
        assertFalse(PostRtpProtectionManager.isProtectedAtTick(second, 10L));
    }

    @Test
    void invalidArgumentsDoNotCreateProtection() {
        UUID playerId = UUID.randomUUID();

        assertDoesNotThrow(() -> PostRtpProtectionManager.grant((ServerPlayer) null, 140));
        PostRtpProtectionManager.grantAtTick(null, 100L, 140);
        PostRtpProtectionManager.grantAtTick(playerId, 100L, 0);
        PostRtpProtectionManager.grantAtTick(playerId, 100L, -1);

        assertFalse(PostRtpProtectionManager.isProtectedAtTick(playerId, 100L));
    }
}
