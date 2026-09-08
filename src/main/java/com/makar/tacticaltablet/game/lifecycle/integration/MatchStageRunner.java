package com.makar.tacticaltablet.game.lifecycle.integration;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Runs an isolated match stage so one subsystem failure cannot skip unrelated cleanup. */
public final class MatchStageRunner {
    private MatchStageRunner() {
    }

    public static boolean run(Runnable action, Consumer<RuntimeException> failureHandler) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(failureHandler, "failureHandler");
        try {
            action.run();
            return true;
        } catch (RuntimeException exception) {
            failureHandler.accept(exception);
            return false;
        }
    }

    public static <T> T call(
            Supplier<T> action,
            T fallback,
            Consumer<RuntimeException> failureHandler
    ) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(failureHandler, "failureHandler");
        try {
            return action.get();
        } catch (RuntimeException exception) {
            failureHandler.accept(exception);
            return fallback;
        }
    }
}
