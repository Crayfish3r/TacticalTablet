package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressServiceArchitectureTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void serviceIsForgeFreeAndDoesNotDependOnFacadeOrPersistence() throws IOException {
        String source = normalizedSource("progression/ProgressService.java");

        assertFalse(source.contains("net.minecraft."));
        assertFalse(source.contains("net.minecraftforge."));
        assertFalse(source.contains("ServerPlayer"));
        assertFalse(source.contains("MinecraftServer"));
        assertFalse(source.contains("PlayerProgressManager"));
        assertFalse(source.contains("AtomicFileStore"));
        assertFalse(source.contains("ModPersistenceExecutor"));
        assertFalse(source.contains("java.nio.file"));
    }

    @Test
    void legacyFacadeDelegatesMutationsToObjectService() throws IOException {
        String source = normalizedSource("progression/PlayerProgressManager.java");

        assertTrue(source.contains("private static final ProgressService PROGRESS_SERVICE"));
        assertTrue(source.contains("PROGRESS_SERVICE.addCoins("));
        assertTrue(source.contains("PROGRESS_SERVICE.purchaseClass("));
        assertTrue(source.contains("PROGRESS_SERVICE.addExperience("));
        assertTrue(source.contains("PROGRESS_SERVICE.unlockBaseClass("));
        assertTrue(source.contains("PROGRESS_SERVICE.upgradeTier("));
        assertTrue(source.contains("PROGRESS_SERVICE.applyIdempotentCredit("));
        assertTrue(source.contains("PROGRESS_SERVICE.grantExclusiveClass("));
    }

    @Test
    void packetHandlerStillUsesLegacyFacadeEntryPoints() throws IOException {
        String source = normalizedSource("tablet/net/TabletPacket.java");

        assertTrue(source.contains("PlayerProgressManager.applyTabletClassPurchase(player, kit, result ->"));
        assertTrue(source.contains("PlayerProgressManager.applyTabletBaseUnlock(player, kit, result ->"));
        assertTrue(source.contains("PlayerProgressManager.applyTabletTierUpgrade(player, kit, targetTier, result ->"));
        assertFalse(source.contains("ProgressService"));
        assertFalse(source.contains("PlayerProgressManager.savePlayer(player)"));
    }

    @Test
    void criticalPublicFacadeMutationsRemainSynchronized() {
        Set<String> criticalNames = Set.of(
                "addXP",
                "setXP",
                "addCoins",
                "setCoins",
                "purchaseClass",
                "unlockBaseClass",
                "upgradeClassTier",
                "applyIdempotentCoinCredit",
                "grantExclusiveClass"
        );
        int checked = 0;
        for (Method method : PlayerProgressManager.class.getDeclaredMethods()) {
            if (!criticalNames.contains(method.getName()) || !Modifier.isPublic(method.getModifiers())) continue;
            checked++;
            assertTrue(Modifier.isStatic(method.getModifiers()), method::toString);
            assertTrue(Modifier.isSynchronized(method.getModifiers()), method::toString);
        }
        assertTrue(checked >= criticalNames.size());
    }

    @Test
    void tabletApplicationFacadesUseExplicitMonitorInsteadOfMethodWideSynchronization() throws Exception {
        for (String name : Set.of(
                "applyTabletClassPurchase",
                "applyTabletBaseUnlock",
                "applyTabletTierUpgrade"
        )) {
            Method method = java.util.Arrays.stream(PlayerProgressManager.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name))
                    .findFirst()
                    .orElseThrow();
            assertTrue(Modifier.isStatic(method.getModifiers()), method::toString);
            assertFalse(Modifier.isSynchronized(method.getModifiers()), method::toString);
        }

        String source = normalizedSource("progression/PlayerProgressManager.java");
        assertTrue(source.contains("synchronized (PlayerProgressManager.class)"));
        assertTrue(source.contains("withProgressLock("));
        assertTrue(source.contains("executePostLockEffects("));
    }

    @Test
    void forgeEventRegistrationRemainsOnExistingServerEventsClass() throws IOException {
        String source = normalizedSource("core/TacticalTabletMod.java");

        assertTrue(source.contains("MinecraftForge.EVENT_BUS.register(ServerEvents.class);"));
        assertFalse(source.contains("ProgressService.class"));
    }

    private static String normalizedSource(String relativePath) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relativePath)).replace("\r\n", "\n");
    }
}
