package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompetitiveClassTierArchitectureTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void policyIsForgeFreeAndHasNoRuntimeOrPersistenceDependencies() throws IOException {
        String source = source("progression/CompetitiveClassTierPolicy.java");

        assertFalse(source.contains("net.minecraft."));
        assertFalse(source.contains("net.minecraftforge."));
        assertFalse(source.contains("ServerPlayer"));
        assertFalse(source.contains("MapSetManager"));
        assertFalse(source.contains("PlayerProgressManager"));
        assertFalse(source.contains("java.nio.file"));
        assertFalse(source.contains("Packet"));
    }

    @Test
    void kitCooldownAppearanceAndTabletStateConsumeTheEffectiveFacadeTier() throws IOException {
        assertTrue(source("progression/kit/KitManager.java").contains("ClassXPManager.getLevel(player, kitName)"));
        assertTrue(source("progression/ClassCooldownManager.java").contains("PlayerProgressManager.getLevel(player, \"sniper\")"));
        assertTrue(source("tablet/TabletAppearanceManager.java").contains("PlayerProgressManager.getLevel(player, clazz)"));

        String classXpManager = source("progression/ClassXPManager.java");
        assertTrue(classXpManager.contains("PlayerProgressManager.getClassTiers(player)"));
        assertTrue(classXpManager.contains("PlayerProgressManager.getAllClassLevels(player)"));
        assertTrue(classXpManager.contains("PlayerProgressManager.getAllClassXP(player)"));

        String tabletScreen = source("tablet/client/TabletScreen.java");
        assertTrue(tabletScreen.contains("TabletClientState.getClassTier(action.classKey())"));
        assertTrue(tabletScreen.contains("ClassTier.clamp(level)"));
    }

    @Test
    void effectiveCompetitiveTierIsNotWrittenToThePlayerSnapshot() throws IOException {
        String source = source("progression/PlayerProgressManager.java");

        assertTrue(source.contains("Map.copyOf(progress.classTiers)"));
        assertFalse(source.contains("progress.classTiers.put(normalizedClass, getCurrentCompetitiveClassTier())"));
        assertFalse(source.contains("progress.classTiers.replaceAll((clazz, tier) -> getCurrentCompetitiveClassTier())"));
    }

    @Test
    void lifecycleRecomputesTheTierForStartCompletionReconnectAndLateJoin() throws IOException {
        String gameState = source("game/GameStateManager.java");
        assertTrue(gameState.contains("case SYNC_CLASS_XP -> ClassXPManager.syncAll(server);"));
        assertTrue(gameState.contains("setComplete = MapSetManager.onGameCompleted(server);\n        }\n        ClassXPManager.syncAll(server);"));

        String serverEvents = source("game/ServerEvents.java");
        assertTrue(serverEvents.contains("ClassXPManager.sync(player);"));
    }

    @Test
    void competitiveStartAnnouncementShowsTheGameAndTierOnlyFromTheStartFlow() throws IOException {
        String mapSetManager = source("game/MapSetManager.java");

        assertTrue(mapSetManager.contains("Соревновательная игра \" + game + \" из \" + GAMES_PER_MAP"));
        assertTrue(mapSetManager.contains("Единый уровень классов: \" + CompetitiveClassTierPolicy.tierForGame(game).displayName()"));
        assertEquals(1, occurrences(mapSetManager, "CompetitiveClassTierPolicy.tierForGame(game).displayName()"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relativePath)).replace("\r\n", "\n");
    }

    private static int occurrences(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
