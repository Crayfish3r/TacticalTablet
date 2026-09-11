package com.makar.tacticaltablet.camouflage;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

public final class CamouflageMapSelection {
    private CamouflageMapSelection() { }

    public static Result resolve(String configuredPreset, Predicate<String> presetExists) {
        if (configuredPreset == null || configuredPreset.isBlank()) return Result.disabled();
        String normalized = configuredPreset.trim().toLowerCase(Locale.ROOT);
        if ("none".equals(normalized)) return Result.disabled();
        return presetExists.test(normalized) ? Result.enabled(normalized) : Result.unknown(normalized);
    }

    public record Result(Optional<String> preset, String unknownPreset) {
        private static Result disabled() { return new Result(Optional.empty(), ""); }
        private static Result enabled(String preset) { return new Result(Optional.of(preset), ""); }
        private static Result unknown(String preset) { return new Result(Optional.empty(), preset); }
        public boolean unknown() { return !unknownPreset.isEmpty(); }
    }
}
