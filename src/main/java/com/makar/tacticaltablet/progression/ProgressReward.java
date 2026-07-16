package com.makar.tacticaltablet.progression;

/** Forge-free reward value. Applying and persisting it remains the caller's responsibility. */
public record ProgressReward(
        int experience,
        int coins,
        boolean levelChanged
) {
}
