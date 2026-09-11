package com.makar.tacticaltablet.camouflage;

import java.util.Locale;
import java.util.Optional;

public enum CamouflagePolicy {
    EMPTY_ONLY,
    REPLACE_AUTO_ONLY;

    static Optional<CamouflagePolicy> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
