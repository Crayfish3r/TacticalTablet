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

class ProgressRepositoryArchitectureTest {
    private static final Path PROGRESSION = Path.of("src/main/java/com/makar/tacticaltablet/progression");

    @Test
    void repositoryHasNoMinecraftForgePacketOrSyncDependencies() throws IOException {
        String source = source("ProgressRepository.java");

        assertFalse(source.contains("net.minecraft."));
        assertFalse(source.contains("net.minecraftforge."));
        assertFalse(source.contains("TabletPacket"));
        assertFalse(source.contains("PacketHandler"));
        assertFalse(source.contains("ClassXPManager.sync"));
        assertFalse(source.contains("ServerPlayer"));
        assertFalse(source.contains("MinecraftServer"));
    }

    @Test
    void repositoryDoesNotContainProgressionMutationsOrCalculations() throws IOException {
        String source = source("ProgressRepository.java");

        assertFalse(source.contains("purchaseClass("));
        assertFalse(source.contains("addExperience("));
        assertFalse(source.contains("upgradeTier("));
        assertFalse(source.contains("calculateLevel("));
        assertFalse(source.contains("MapSetManager"));
        assertFalse(source.contains("ProgressService"));
    }

    @Test
    void serviceRemainsIndependentFromRepository() throws IOException {
        assertFalse(source("ProgressService.java").contains("ProgressRepository"));
    }

    @Test
    void managerDelegatesAllFilePersistenceOperations() throws IOException {
        String manager = source("PlayerProgressManager.java");

        assertTrue(manager.contains("progressRepository.loadByKey("));
        assertTrue(manager.contains("progressRepository.findByUuid("));
        assertTrue(manager.contains("progressRepository.save("));
        assertTrue(manager.contains("progressRepository.backup("));
        assertTrue(manager.contains("progressRepository.flush("));
        assertFalse(manager.contains("Files."));
        assertFalse(manager.contains("new AtomicFileStore"));
        assertFalse(manager.contains("GSON.toJson"));
        assertFalse(manager.contains("GSON.fromJson"));
    }

    @Test
    void criticalFacadeMethodsRemainSynchronized() {
        Set<String> names = Set.of(
                "loadPlayer", "savePlayer", "saveAll", "backupNow", "flushForShutdown",
                "addXP", "addCoins", "purchaseClass", "applyIdempotentCoinCredit",
                "grantExclusiveClass"
        );
        for (Method method : PlayerProgressManager.class.getDeclaredMethods()) {
            if (!names.contains(method.getName()) || !Modifier.isPublic(method.getModifiers())) continue;
            assertTrue(Modifier.isStatic(method.getModifiers()), method::toString);
            assertTrue(Modifier.isSynchronized(method.getModifiers()), method::toString);
        }
    }

    @Test
    void packetAndForgeRegistrationSourcesRemainOutsideRepositoryDiffBoundary() throws IOException {
        String packet = Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/tablet/net/TabletPacket.java")).replace("\r\n", "\n");
        String mod = Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/core/TacticalTabletMod.java")).replace("\r\n", "\n");

        assertTrue(packet.contains("PlayerProgressManager.purchaseClass(player, kit)"));
        assertFalse(packet.contains("ProgressRepository"));
        assertTrue(mod.contains("MinecraftForge.EVENT_BUS.register(ServerEvents.class);"));
    }

    private static String source(String file) throws IOException {
        return Files.readString(PROGRESSION.resolve(file)).replace("\r\n", "\n");
    }
}
