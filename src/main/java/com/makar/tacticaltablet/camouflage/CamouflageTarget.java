package com.makar.tacticaltablet.camouflage;

import java.util.Locale;
import java.util.Optional;

public record CamouflageTarget(Kind kind, String slot) {
    public enum Kind { ARMOR, CURIOS }

    static Optional<CamouflageTarget> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "minecraft:head" -> Optional.of(new CamouflageTarget(Kind.ARMOR, "head"));
            case "minecraft:chest" -> Optional.of(new CamouflageTarget(Kind.ARMOR, "chest"));
            case "minecraft:legs" -> Optional.of(new CamouflageTarget(Kind.ARMOR, "legs"));
            case "minecraft:feet" -> Optional.of(new CamouflageTarget(Kind.ARMOR, "feet"));
            case "curios:mask" -> Optional.of(new CamouflageTarget(Kind.CURIOS, "mask"));
            case "curios:uniform" -> Optional.of(new CamouflageTarget(Kind.CURIOS, "uniform"));
            default -> Optional.empty();
        };
    }

    public String markerPiece() {
        return kind.name().toLowerCase(Locale.ROOT) + ":" + slot;
    }
}
