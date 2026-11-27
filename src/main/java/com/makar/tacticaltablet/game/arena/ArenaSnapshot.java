package com.makar.tacticaltablet.game.arena;

import java.util.List;
import java.util.UUID;

/** Immutable server-thread extraction, also retained by the client for the final table. */
public record ArenaSnapshot(UUID matchId, boolean active, boolean finished, int secondsLeft,
                            int gameNumber, int gamesPerSet, List<Row> rows, boolean setResults) {
    public static final ArenaSnapshot EMPTY = new ArenaSnapshot(null, false, false, 0, 0, 3, List.of());
    public ArenaSnapshot { rows = List.copyOf(rows); }
    public ArenaSnapshot(UUID matchId, boolean active, boolean finished, int secondsLeft,
                         int gameNumber, int gamesPerSet, List<Row> rows) {
        this(matchId, active, finished, secondsLeft, gameNumber, gamesPerSet, rows, false);
    }
    public record Row(UUID playerId, String name, int score, int pvpKills, int npcKills, int assists, int deaths) { }
}
