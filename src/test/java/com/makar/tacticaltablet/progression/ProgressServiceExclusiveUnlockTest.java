package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressServiceExclusiveUnlockTest {
    private static final ProgressService SERVICE = new ProgressService(new ProgressCatalog(
            Set.of("scout"), Set.of("scout"), Map.of(), Map.of(), Set.of("medic"), 100));

    @Test
    void grantAddsPurchaseAndMissingExperienceEntry() {
        TestMutableProgressState state = new TestMutableProgressState(20);

        ExclusiveUnlockResult result = SERVICE.grantExclusiveClass(state, " Medic ");

        assertEquals(ExclusiveUnlockResult.Status.GRANTED, result.status());
        assertTrue(result.changed());
        assertEquals(new ProgressEntry(true, 1), state.purchase("medic"));
        assertEquals(new ProgressEntry(true, 0), state.experience("medic"));
        assertEquals(20, state.coins());
    }

    @Test
    void alreadyOwnedAndInvalidDoNotMutateState() {
        TestMutableProgressState state = new TestMutableProgressState(20);
        state.purchase("medic", 1);
        state.experience("medic", 42);

        assertEquals(ExclusiveUnlockResult.Status.ALREADY_OWNED,
                SERVICE.grantExclusiveClass(state, "medic").status());
        assertEquals(ExclusiveUnlockResult.Status.INVALID_CLASS,
                SERVICE.grantExclusiveClass(state, "unknown").status());
        assertEquals(new ProgressEntry(true, 42), state.experience("medic"));
        assertEquals(20, state.coins());
    }

    @Test
    void rollbackRestoresAbsentEntriesAndIsIdempotent() {
        TestMutableProgressState state = new TestMutableProgressState(20);
        ExclusiveUnlockRollback rollback = SERVICE.grantExclusiveClass(state, "medic").rollback().orElseThrow();

        assertTrue(SERVICE.rollbackExclusiveClass(state, rollback));
        assertFalse(SERVICE.rollbackExclusiveClass(state, rollback));
        assertFalse(state.purchase("medic").present());
        assertFalse(state.experience("medic").present());
    }

    @Test
    void rollbackRestoresPreexistingValuesWithoutTouchingIndependentMutations() {
        TestMutableProgressState state = new TestMutableProgressState(20);
        state.purchase("medic", 0);
        state.experience("medic", 42);
        ExclusiveUnlockRollback rollback = SERVICE.grantExclusiveClass(state, "medic").rollback().orElseThrow();
        state.purchase("other", 1);
        SERVICE.addCoins(state, 5);

        assertTrue(SERVICE.rollbackExclusiveClass(state, rollback));
        assertEquals(new ProgressEntry(true, 0), state.purchase("medic"));
        assertEquals(new ProgressEntry(true, 42), state.experience("medic"));
        assertEquals(new ProgressEntry(true, 1), state.purchase("other"));
        assertEquals(25, state.coins());
    }

    @Test
    void rollbackRefusesToOverwriteLaterMutationOfGrantedClass() {
        TestMutableProgressState state = new TestMutableProgressState(20);
        ExclusiveUnlockRollback rollback = SERVICE.grantExclusiveClass(state, "medic").rollback().orElseThrow();
        state.experience("medic", 5);

        assertFalse(SERVICE.rollbackExclusiveClass(state, rollback));
        assertEquals(new ProgressEntry(true, 1), state.purchase("medic"));
        assertEquals(new ProgressEntry(true, 5), state.experience("medic"));
    }

    @Test
    void serviceStatusMapsExactlyToLegacyFacadeResult() {
        TestMutableProgressState state = new TestMutableProgressState(0);
        ExclusiveUnlockResult granted = SERVICE.grantExclusiveClass(state, "medic");
        ExclusiveUnlockResult owned = SERVICE.grantExclusiveClass(state, "medic");
        ExclusiveUnlockResult invalid = SERVICE.grantExclusiveClass(state, "unknown");

        assertEquals(PlayerProgressManager.ExclusiveClassGrantResult.GRANTED,
                PlayerProgressManager.mapExclusiveUnlockResult(granted));
        assertEquals(PlayerProgressManager.ExclusiveClassGrantResult.ALREADY_OWNED,
                PlayerProgressManager.mapExclusiveUnlockResult(owned));
        assertEquals(PlayerProgressManager.ExclusiveClassGrantResult.INVALID_CLASS,
                PlayerProgressManager.mapExclusiveUnlockResult(invalid));
    }
}
