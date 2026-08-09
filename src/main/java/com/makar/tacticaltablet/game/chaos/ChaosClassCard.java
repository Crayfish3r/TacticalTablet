package com.makar.tacticaltablet.game.chaos;

import com.makar.tacticaltablet.progression.ClassTier;

public record ChaosClassCard(String classId, int tier) {
    private static final String SEPARATOR = "@";

    public ChaosClassCard {
        classId = classId == null ? "" : classId.trim();
        if (classId.isBlank() || classId.contains(SEPARATOR)) {
            throw new IllegalArgumentException("Invalid Chaos class id: " + classId);
        }
        tier = ClassTier.clamp(tier).id();
    }

    public String encode() { return classId + SEPARATOR + tier; }

    public static ChaosClassCard decode(String encoded) {
        if (encoded == null) throw new IllegalArgumentException("Chaos card is null");
        int separator = encoded.lastIndexOf(SEPARATOR);
        if (separator <= 0 || separator == encoded.length() - 1) {
            throw new IllegalArgumentException("Invalid Chaos card: " + encoded);
        }
        try {
            return new ChaosClassCard(encoded.substring(0, separator),
                    Integer.parseInt(encoded.substring(separator + 1)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Chaos card tier: " + encoded, exception);
        }
    }

    static String identity(String encoded) {
        int separator = encoded == null ? -1 : encoded.lastIndexOf(SEPARATOR);
        return separator > 0 ? encoded.substring(0, separator) : encoded;
    }
}
