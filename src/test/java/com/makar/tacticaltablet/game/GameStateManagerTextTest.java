package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameStateManagerTextTest {
    @Test
    void absentClanWarWinnerUsesExactUtf8Label() {
        assertEquals("Нет победителя", ClanWarWinnerLabel.resolve(null));
    }
}
