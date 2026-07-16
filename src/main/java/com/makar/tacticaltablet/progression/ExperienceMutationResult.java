package com.makar.tacticaltablet.progression;

record ExperienceMutationResult(
        boolean changed,
        int previousExperience,
        int currentExperience,
        int calculatedLevel,
        int savedTier
) {
    int awardedExperience() {
        return Math.max(0, currentExperience - previousExperience);
    }
}
