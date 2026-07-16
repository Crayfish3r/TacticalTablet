package com.makar.tacticaltablet.progression;

import java.util.List;
import java.util.Objects;

/** Immutable level thresholds supplied to the pure progression policy. */
public record ProgressionRules(
        int maximumLevel,
        int maximumExperience,
        List<Integer> experienceThresholds
) {
    public ProgressionRules {
        Objects.requireNonNull(experienceThresholds, "experienceThresholds");
        experienceThresholds = List.copyOf(experienceThresholds);
        if (maximumLevel < 0 || maximumExperience < 0 || experienceThresholds.size() <= maximumLevel) {
            throw new IllegalArgumentException("Progression rules do not describe every level");
        }
        int previous = -1;
        for (int threshold : experienceThresholds) {
            if (threshold < 0 || threshold < previous || threshold > maximumExperience) {
                throw new IllegalArgumentException("Experience thresholds must be ordered and bounded");
            }
            previous = threshold;
        }
    }
}
