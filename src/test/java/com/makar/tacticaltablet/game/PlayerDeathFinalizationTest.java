package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerDeathFinalizationTest {
    @Test
    void finalNormalKillRecordsAllDeathConsequencesBeforeMatchEnd() {
        List<String> actions = new ArrayList<>();

        PlayerDeathFinalization.process(
                true,
                () -> actions.addAll(List.of(
                        "death",
                        "lives",
                        "kill",
                        "discord-kill",
                        "kill-coins",
                        "contract"
                )),
                () -> actions.add("match-end")
        );

        assertEquals(List.of(
                "death",
                "lives",
                "kill",
                "discord-kill",
                "kill-coins",
                "contract",
                "match-end"
        ), actions);
    }

    @Test
    void zoneDeathWithoutKillerStillChecksMatchEnd() {
        List<String> actions = new ArrayList<>();

        PlayerDeathFinalization.process(
                true,
                () -> actions.addAll(List.of(
                        "death",
                        "lives",
                        "contract/environment processing"
                )),
                () -> actions.add("match-end")
        );

        assertEquals(List.of(
                "death",
                "lives",
                "contract/environment processing",
                "match-end"
        ), actions);
    }

    @Test
    void teamKillRecordsTeamKillAndThenChecksMatchEndWithoutNormalReward() {
        List<String> actions = new ArrayList<>();

        PlayerDeathFinalization.process(
                true,
                () -> actions.addAll(List.of(
                        "death",
                        "lives",
                        "team-kill",
                        "contract"
                )),
                () -> actions.add("match-end")
        );

        assertEquals(List.of(
                "death",
                "lives",
                "team-kill",
                "contract",
                "match-end"
        ), actions);
    }

    @Test
    void suicideDoesNotRecordNormalKillOrCoinsButStillChecksMatchEnd() {
        List<String> actions = new ArrayList<>();

        PlayerDeathFinalization.process(
                true,
                () -> actions.addAll(List.of(
                        "death",
                        "lives",
                        "contract"
                )),
                () -> actions.add("match-end")
        );

        assertEquals(List.of(
                "death",
                "lives",
                "contract",
                "match-end"
        ), actions);
    }

    @Test
    void nonParticipantDoesNotCheckMatchEnd() {
        List<String> actions = new ArrayList<>();

        PlayerDeathFinalization.process(
                false,
                () -> actions.add("death-accounting"),
                () -> actions.add("match-end")
        );

        assertEquals(List.of("death-accounting"), actions);
    }

    @Test
    void deathAccountingExceptionSkipsMatchEndAndPropagates() {
        List<String> actions = new ArrayList<>();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> PlayerDeathFinalization.process(
                        true,
                        () -> {
                            actions.add("death");
                            throw new IllegalStateException("boom");
                        },
                        () -> actions.add("match-end")
                )
        );

        assertEquals("boom", exception.getMessage());
        assertEquals(List.of("death"), actions);
    }
}
