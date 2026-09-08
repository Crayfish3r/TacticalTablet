package com.makar.tacticaltablet.game.lobby;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LobbyChunkRangeTest {
    @Test
    void rangeUsesActualStructureBoundsAndOneChunkMargin() {
        LobbyChunkRange withoutMargin = LobbyChunkRange.fromStructure(-10, -10, 20, 20, 0);
        assertEquals(new LobbyChunkRange(-1, 0, -1, 0), withoutMargin);

        LobbyChunkRange protectedRange = LobbyChunkRange.fromStructure(-10, -10, 20, 20, 1);
        assertEquals(new LobbyChunkRange(-2, 1, -2, 1), protectedRange);
        assertEquals(16, protectedRange.chunkCount());
    }

    @Test
    void repeatedResidencyEnumerationIsIdempotent() {
        LobbyChunkRange range = LobbyChunkRange.fromStructure(-10, -10, 20, 20, 1);
        Set<LobbyChunkRange.ChunkCoordinate> forced = new HashSet<>(range.positions());
        forced.addAll(range.positions());

        assertEquals(range.chunkCount(), forced.size());
    }

    @Test
    void invalidTemplateDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> LobbyChunkRange.fromStructure(-10, -10, 0, 20, 1));
        assertThrows(IllegalArgumentException.class,
                () -> LobbyChunkRange.fromStructure(-10, -10, 20, 20, -1));
    }
}
