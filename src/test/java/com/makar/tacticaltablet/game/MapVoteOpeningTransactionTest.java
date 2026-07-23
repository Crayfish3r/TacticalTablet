package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapVoteOpeningTransactionTest {
    @Test
    void failedSaveRestoresMutatedFieldsAndDoesNotPublishVote() {
        MutableState state = new MutableState(List.of("old"), true);
        List<String> previousHistory = List.copyOf(state.history);
        boolean previousMode = state.nextCompetitive;
        state.history = new ArrayList<>(List.of("new"));
        state.nextCompetitive = false;

        boolean committed = MapVoteOpeningTransaction.commit(
                () -> false,
                () -> {
                    state.history = new ArrayList<>(previousHistory);
                    state.nextCompetitive = previousMode;
                }
        );
        boolean voting = committed;

        assertFalse(committed);
        assertFalse(voting);
        assertEquals(List.of("old"), state.history);
        assertTrue(state.nextCompetitive);
    }

    @Test
    void successfulSaveKeepsMutationAndAllowsVotePublication() {
        MutableState state = new MutableState(List.of("new"), false);

        boolean committed = MapVoteOpeningTransaction.commit(
                () -> true,
                () -> {
                    throw new AssertionError("rollback must not run");
                }
        );

        assertTrue(committed);
        assertEquals(List.of("new"), state.history);
        assertFalse(state.nextCompetitive);
    }

    private static final class MutableState {
        private List<String> history;
        private boolean nextCompetitive;

        private MutableState(List<String> history, boolean nextCompetitive) {
            this.history = new ArrayList<>(history);
            this.nextCompetitive = nextCompetitive;
        }
    }
}
