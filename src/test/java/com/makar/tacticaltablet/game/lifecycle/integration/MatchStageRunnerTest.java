package com.makar.tacticaltablet.game.lifecycle.integration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchStageRunnerTest {
    @Test
    void runtimeFailureIsReportedWithoutEscapingToTheCoordinator() {
        List<RuntimeException> failures = new ArrayList<>();
        boolean completed = MatchStageRunner.run(
                () -> { throw new IllegalStateException("voice unavailable"); },
                failures::add
        );

        assertFalse(completed);
        assertEquals(1, failures.size());
        assertEquals("voice unavailable", failures.get(0).getMessage());
    }

    @Test
    void independentStageCanContinueAfterEarlierFailure() {
        List<String> completed = new ArrayList<>();
        MatchStageRunner.run(() -> { throw new IllegalStateException("failed"); }, ignored -> { });
        assertTrue(MatchStageRunner.run(() -> completed.add("cleanup"), ignored -> { }));
        assertEquals(List.of("cleanup"), completed);
    }

    @Test
    void valueStageUsesFallbackOnlyOnFailure() {
        assertEquals(7, MatchStageRunner.call(() -> 7, -1, ignored -> { }));
        assertEquals(-1, MatchStageRunner.call(
                () -> { throw new IllegalStateException("failed"); },
                -1,
                ignored -> { }
        ));
    }
}
