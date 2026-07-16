package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void successfulPurchasePreservesDirtyResponseSaveSyncOrder() {
        TestMutableProgressState state = new TestMutableProgressState(100);
        RecordingSideEffects effects = new RecordingSideEffects();

        ProgressApplicationResult<ProgressPurchaseResult> application = APPLICATION.purchaseClass(
                state,
                "sniper",
                new ProgressContext(false),
                effects,
                result -> {
                    assertTrue(result.successful());
                    assertEquals(50, state.coins());
                    assertEquals(1, state.purchase("sniper").value());
                    effects.events.add("response");
                }
        );

        assertTrue(application.changed());
        assertTrue(application.outcome().successful());
        assertEquals(List.of("dirty", "response", "save", "sync"), effects.events);
    }

    @Test
    void rejectedPurchaseRespondsAndSyncsWithoutDirtyOrSave() {
        TestMutableProgressState state = new TestMutableProgressState(49);
        RecordingSideEffects effects = new RecordingSideEffects();

        ProgressApplicationResult<ProgressPurchaseResult> application = APPLICATION.purchaseClass(
                state,
                "sniper",
                new ProgressContext(false),
                effects,
                result -> effects.events.add("response:" + result.failure())
        );

        assertFalse(application.changed());
        assertEquals(49, state.coins());
        assertEquals(0, state.purchase("sniper").value());
        assertEquals(List.of("response:INSUFFICIENT_FUNDS", "sync"), effects.events);
    }

    @Test
    void alreadyOwnedPurchaseDoesNotDuplicateMutationSaveOrSync() {
        TestMutableProgressState state = new TestMutableProgressState(100);
        state.purchase("sniper", 1);
        RecordingSideEffects effects = new RecordingSideEffects();

        APPLICATION.purchaseClass(
                state,
                "sniper",
                new ProgressContext(false),
                effects,
                result -> effects.events.add("response:" + result.failure())
        );

        assertEquals(100, state.coins());
        assertEquals(1, state.purchase("sniper").value());
        assertEquals(List.of("response:ALREADY_OWNED", "sync"), effects.events);
    }

    @Test
    void baseUnlockUsesTheSameApplicationSideEffectBoundary() {
        TestMutableProgressState state = new TestMutableProgressState(100);
        RecordingSideEffects effects = new RecordingSideEffects();

        ProgressApplicationResult<BaseUnlockResult> application = APPLICATION.unlockBaseClass(
                state,
                "medic",
                new ProgressContext(false),
                effects,
                result -> effects.events.add("response:" + result.status())
        );

        assertTrue(application.changed());
        assertEquals(0, state.coins());
        assertEquals(1, state.baseUnlock("medic").value());
        assertEquals(List.of("dirty", "response:SUCCESS", "save", "sync"), effects.events);
    }

    @Test
    void tierFailureKeepsStateAndOnlyRespondsThenSyncs() {
        TestMutableProgressState state = new TestMutableProgressState(500);
        state.baseUnlock("medic", 1);
        state.tier("medic", ClassTier.BASIC.id());
        state.experience("medic", 0);
        RecordingSideEffects effects = new RecordingSideEffects();

        ProgressApplicationResult<TierUpgradeResult> application = APPLICATION.upgradeTier(
                state,
                "medic",
                ClassTier.RARE.id(),
                new ProgressContext(false),
                effects,
                result -> effects.events.add("response:" + result.status())
        );

        assertFalse(application.changed());
        assertEquals(ClassTier.BASIC.id(), state.tier("medic").value());
        assertEquals(500, state.coins());
        assertEquals(List.of("response:NOT_ENOUGH_XP", "sync"), effects.events);
    }

    @Test
    void separateApplicationsDoNotRetainPlayerState() {
        TestMutableProgressState first = new TestMutableProgressState(100);
        TestMutableProgressState second = new TestMutableProgressState(100);

        APPLICATION.purchaseClass(first, "sniper", new ProgressContext(false),
                new RecordingSideEffects(), ignored -> { });

        assertEquals(50, first.coins());
        assertEquals(100, second.coins());
        assertEquals(0, second.purchase("sniper").value());
    }

    private static final class RecordingSideEffects implements ProgressApplicationService.SideEffects {
        private final List<String> events = new ArrayList<>();

        @Override
        public void markDirty() {
            events.add("dirty");
        }

        @Override
        public void queueSave() {
            events.add("save");
        }

        @Override
        public void sync() {
            events.add("sync");
        }
    }
}
