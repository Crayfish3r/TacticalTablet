package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompetitiveProgressionCharacterizationTest {
    private static final Path PLAYER_PROGRESS_MANAGER = Path.of(
            "src/main/java/com/makar/tacticaltablet/progression/PlayerProgressManager.java"
    );

    @Test
    void competitiveModeUsesTheTemporaryTierPolicyWithoutPersistingIt() throws IOException {
        String source = normalizedSource();

        assertTrue(source.contains("CompetitiveClassTierPolicy.effectiveBaseTier("));
        assertTrue(source.contains("CompetitiveClassTierPolicy.tierForGame(MapSetManager.getCurrentGameNumber()).id()"));
        assertTrue(source.contains("Map.copyOf(progress.classTiers)"));
    }

    @Test
    void competitiveAvailabilityAndEconomyUseSeparateGuards() throws IOException {
        String source = normalizedSource();

        assertTrue(source.contains("if (MapSetManager.isCompetitiveSet()) return true;"));
        assertTrue(source.contains("if (MapSetManager.isCompetitiveSet()) return false;"));
        assertTrue(source.contains("if (MapSetManager.isCompetitiveSet()) return PurchaseResult.NOT_PURCHASABLE;"));
        assertTrue(source.contains("if (MapSetManager.isCompetitiveSet()) return ProgressionResult.WRONG_TIER;"));
    }

    private static String normalizedSource() throws IOException {
        return Files.readString(PLAYER_PROGRESS_MANAGER).replace("\r\n", "\n");
    }
}
