package com.makar.tacticaltablet.game.arena;

import com.google.gson.GsonBuilder;
import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.FileSaveResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Snapshot loaded before start; coefficients never change halfway through a game. */
public record ArenaScoringRules(int playerKill, int assist, double damagePerPoint, int npcKill) {
    public ArenaScoringRules {
        if (playerKill < 0 || playerKill > 1_000_000 || assist < 0 || assist > 1_000_000
                || npcKill < 0 || npcKill > 1_000_000 || !Double.isFinite(damagePerPoint)
                || damagePerPoint <= 0 || damagePerPoint > 1_000_000)
            throw new IllegalArgumentException("Invalid Arena scoring coefficients");
    }
    public int score(int kills, int assists, double damage, int npcs) {
        double value = (long) kills * playerKill + (long) assists * assist + (long) npcs * npcKill
                + Math.floor(damage / damagePerPoint);
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }
    public static ArenaScoringRules load(Path path) throws IOException {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        if (!Files.exists(path)) {
            var defaults = new ArenaScoringRules(2, 1, 20, 1);
            var saved = new AtomicFileStore().writeUtf8(path, gson.toJson(defaults));
            if (saved.status() != FileSaveResult.Status.SUCCESS) throw new IOException(saved.diagnostic());
            return defaults;
        }
        if (Files.size(path) > 16384) throw new IOException("Arena scoring config exceeds 16 KiB");
        try {
            var value = gson.fromJson(Files.readString(path), ArenaScoringRules.class);
            if (value == null) throw new IllegalArgumentException("Arena scoring config is null");
            return value;
        } catch (RuntimeException error) { throw new IOException("Invalid Arena scoring config: " + error.getMessage(), error); }
    }
}
