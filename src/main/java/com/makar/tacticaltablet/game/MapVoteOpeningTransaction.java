package com.makar.tacticaltablet.game;

import java.util.Objects;
import java.util.function.BooleanSupplier;

final class MapVoteOpeningTransaction {
    private MapVoteOpeningTransaction() {
    }

    static boolean commit(BooleanSupplier save, Runnable rollback) {
        Objects.requireNonNull(save, "save");
        Objects.requireNonNull(rollback, "rollback");
        if (save.getAsBoolean()) return true;
        rollback.run();
        return false;
    }
}
