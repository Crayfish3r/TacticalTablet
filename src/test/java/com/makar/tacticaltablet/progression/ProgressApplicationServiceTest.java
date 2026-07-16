package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressApplicationServiceTest {
    private static final ProgressApplicationService APPLICATION = new ProgressApplicationService(
            new ProgressService(new ProgressCatalog(
                    Set.of("scout"),
                    Set.of("scout", "medic"),
                    Map.of("sniper", 50),
                    Map.of("sniper", ClassTier.RARE.id()),
                    Set.of("saboteur"),
                    100
            ))
    );

    @Test
    void successfulPurchasePreservesDirtyResponseSaveSyncOrderAndLockScope() {
        TestMutableProgressState state = new TestMutableProgressState(100);
        RecordingSideEffects effects = new RecordingSideEffects();
        List<String> events = effects.events;

        PreparedProgressOperation<ProgressPurchaseResult> operation = PlayerProgressManager.withProgressLock(() -> {
            assertTrue(Thread.holdsLock(PlayerProgressManager.class));
            ProgressApplicationResult<ProgressPurchaseResult> application =
                    APPLICATION.prepareClassPurchase(state, "sniper", new ProgressContext(false));
            events.add("mutation");
            if (application.changed()) events.add("dirty/revision");
            return PreparedProgressOperation.withSave(
                    application.outcome(), save(1, state.coins()), ProgressSyncMode.TABLET);
        });

        ProgressPurchaseResult result = APPLICATION.executePostLockEffects(
                operation,
                response -> {
                    assertFalse(Thread.holdsLock(PlayerProgressManager.class));
                    events.add("response");
                },
                effects
        );

        assertTrue(result.successful());
        assertEquals(50, state.coins());
        assertEquals(1, state.purchase("sniper").value());
        assertEquals(List.of("mutation", "dirty/revision", "response", "save", "sync"), events);
    }

    @Test
    void rejectedPurchaseRespondsAndSyncsWithoutDirtyOrSave() {
        TestMutableProgressState state = new TestMutableProgressState(49);
        RecordingSideEffects effects = new RecordingSideEffects();
        ProgressApplicationResult<ProgressPurchaseResult> application = PlayerProgressManager.withProgressLock(() ->
                APPLICATION.prepareClassPurchase(state, "sniper", new ProgressContext(false)));
        PreparedProgressOperation<ProgressPurchaseResult> operation = PreparedProgressOperation.withoutSave(
                application.outcome(), ProgressSyncMode.TABLET);

        APPLICATION.executePostLockEffects(
                operation,
                result -> effects.events.add("response:" + result.failure()),
                effects
        );

        assertFalse(application.changed());
        assertEquals(49, state.coins());
        assertEquals(0, state.purchase("sniper").value());
        assertEquals(List.of("response:INSUFFICIENT_FUNDS", "sync"), effects.events);
    }

    @Test
    void baseUnlockAndTierUpgradeUseTheSamePreparedBoundary() {
        TestMutableProgressState unlockState = new TestMutableProgressState(100);
        ProgressApplicationResult<BaseUnlockResult> unlock = PlayerProgressManager.withProgressLock(() ->
                APPLICATION.prepareBaseUnlock(unlockState, "medic", new ProgressContext(false)));

        TestMutableProgressState tierState = new TestMutableProgressState(500);
        tierState.baseUnlock("medic", 1);
        tierState.tier("medic", ClassTier.BASIC.id());
        tierState.experience("medic", 0);
        ProgressApplicationResult<TierUpgradeResult> tier = PlayerProgressManager.withProgressLock(() ->
                APPLICATION.prepareTierUpgrade(
                        tierState, "medic", ClassTier.RARE.id(), new ProgressContext(false)));

        assertTrue(unlock.changed());
        assertEquals(0, unlockState.coins());
        assertEquals(1, unlockState.baseUnlock("medic").value());
        assertFalse(tier.changed());
        assertEquals(ProgressionStatus.NOT_ENOUGH_XP, tier.outcome().status());
        assertEquals(ClassTier.BASIC.id(), tierState.tier("medic").value());
    }

    @Test
    void successfulBaseUnlockPreservesMutationDirtyResponseSaveSyncOrder() {
        TestMutableProgressState state = new TestMutableProgressState(100);
        RecordingSideEffects effects = new RecordingSideEffects();
        PreparedProgressOperation<BaseUnlockResult> operation = PlayerProgressManager.withProgressLock(() -> {
            ProgressApplicationResult<BaseUnlockResult> result = APPLICATION.prepareBaseUnlock(
                    state, "medic", new ProgressContext(false));
            effects.events.add("mutation");
            effects.events.add("dirty/revision");
            return PreparedProgressOperation.withSave(
                    result.outcome(), save(1, state.coins()), ProgressSyncMode.TABLET);
        });

        APPLICATION.executePostLockEffects(
                operation, result -> effects.events.add("response"), effects);

        assertEquals(List.of("mutation", "dirty/revision", "response", "save", "sync"), effects.events);
    }

    @Test
    void successfulTierUpgradePreservesMutationDirtyResponseSaveSyncOrder() {
        TestMutableProgressState state = new TestMutableProgressState(500);
        state.baseUnlock("medic", 1);
        state.tier("medic", ClassTier.BASIC.id());
        state.experience("medic", ClassTier.RARE.requiredXp());
        RecordingSideEffects effects = new RecordingSideEffects();
        PreparedProgressOperation<TierUpgradeResult> operation = PlayerProgressManager.withProgressLock(() -> {
            ProgressApplicationResult<TierUpgradeResult> result = APPLICATION.prepareTierUpgrade(
                    state, "medic", ClassTier.RARE.id(), new ProgressContext(false));
            effects.events.add("mutation");
            effects.events.add("dirty/revision");
            return PreparedProgressOperation.withSave(
                    result.outcome(), save(1, state.coins()), ProgressSyncMode.TABLET);
        });

        APPLICATION.executePostLockEffects(
                operation, result -> effects.events.add("response"), effects);

        assertEquals(List.of("mutation", "dirty/revision", "response", "save", "sync"), effects.events);
    }

    @Test
    void responseExceptionPreventsSaveAndSyncAndIsNotHidden() {
        RecordingSideEffects effects = new RecordingSideEffects();
        PreparedProgressOperation<String> operation = PreparedProgressOperation.withSave(
                "success", save(1, 50), ProgressSyncMode.TABLET);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                APPLICATION.executePostLockEffects(operation, result -> {
                    effects.events.add("response");
                    throw new IllegalStateException("response failed");
                }, effects));

        assertEquals("response failed", failure.getMessage());
        assertEquals(List.of("response"), effects.events);
    }

    @Test
    void saveExceptionPreventsSyncAndIsNotHidden() {
        List<String> events = new ArrayList<>();
        ProgressApplicationService.SideEffects effects = new ProgressApplicationService.SideEffects() {
            public void enqueueSave(QueuedProgressSave save) {
                assertFalse(Thread.holdsLock(PlayerProgressManager.class));
                events.add("save");
                throw new IllegalStateException("save failed");
            }
            public void sync(ProgressSyncMode mode) { events.add("sync"); }
        };
        PreparedProgressOperation<String> operation = PreparedProgressOperation.withSave(
                "success", save(1, 50), ProgressSyncMode.TABLET);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                APPLICATION.executePostLockEffects(
                        operation, result -> events.add("response"), effects));

        assertEquals("save failed", failure.getMessage());
        assertEquals(List.of("response", "save"), events);
    }

    @Test
    void syncExceptionOccursAfterSaveAndIsNotHidden() {
        List<String> events = new ArrayList<>();
        ProgressApplicationService.SideEffects effects = new ProgressApplicationService.SideEffects() {
            public void enqueueSave(QueuedProgressSave save) { events.add("save"); }
            public void sync(ProgressSyncMode mode) {
                assertFalse(Thread.holdsLock(PlayerProgressManager.class));
                events.add("sync");
                throw new IllegalStateException("sync failed");
            }
        };
        PreparedProgressOperation<String> operation = PreparedProgressOperation.withSave(
                "success", save(1, 50), ProgressSyncMode.TABLET);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                APPLICATION.executePostLockEffects(
                        operation, result -> events.add("response"), effects));

        assertEquals("sync failed", failure.getMessage());
        assertEquals(List.of("response", "save", "sync"), events);
    }

    static QueuedProgressSave save(long revision, int coins) {
        return new QueuedProgressSave(new ProgressSnapshot(
                "player",
                revision,
                new ProgressSnapshot.Data(
                        11, "Player", "00000000000000000000000000000000",
                        Map.of(), Map.of(), Map.of(),
                        0, 0, 0, 0, coins, 0,
                        false, false,
                        Map.of(), Map.of(), Map.of(), List.of(),
                        1L, 2L
                )
        ));
    }

    private static final class RecordingSideEffects implements ProgressApplicationService.SideEffects {
        private final List<String> events = new ArrayList<>();

        @Override
        public void enqueueSave(QueuedProgressSave save) {
            assertFalse(Thread.holdsLock(PlayerProgressManager.class));
            events.add("save");
        }

        @Override
        public void sync(ProgressSyncMode mode) {
            assertFalse(Thread.holdsLock(PlayerProgressManager.class));
            events.add("sync");
        }
    }
}
