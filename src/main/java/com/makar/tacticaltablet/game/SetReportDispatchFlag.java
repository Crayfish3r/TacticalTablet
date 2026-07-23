package com.makar.tacticaltablet.game;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class SetReportDispatchFlag {
    private SetReportDispatchFlag() {
    }

    static boolean persistTrue(
            boolean previousValue,
            Consumer<Boolean> setter,
            BooleanSupplier save
    ) {
        Objects.requireNonNull(setter, "setter");
        Objects.requireNonNull(save, "save");
        setter.accept(true);
        if (save.getAsBoolean()) return true;
        setter.accept(previousValue);
        return false;
    }
}
