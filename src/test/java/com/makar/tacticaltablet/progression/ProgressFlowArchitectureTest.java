package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressFlowArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void domainAndRepositoryRemainIndependentFromSyncAndPackets() throws IOException {
        String service = source("progression/ProgressService.java");
        String repository = source("progression/ProgressRepository.java");

        assertFalse(service.contains("ProgressSyncService"));
        assertFalse(service.contains("PacketHandler"));
        assertFalse(service.contains("ProgressRepository"));
        assertFalse(repository.contains("ProgressSyncService"));
        assertFalse(repository.contains("PacketHandler"));
    }

    @Test
    void syncBoundaryContainsNoPersistenceMutationOrMessages() throws IOException {
        String sync = source("progression/ProgressSyncService.java");

        assertFalse(sync.contains("ProgressRepository"));
        assertFalse(sync.contains("ModPersistenceExecutor"));
        assertFalse(sync.contains("java.nio.file"));
        assertFalse(sync.contains("ProgressService"));
        assertFalse(sync.contains("markDirty"));
        assertFalse(sync.contains("sendSystemMessage"));
        assertTrue(sync.contains("ClassXPManager.sync("));
        assertTrue(sync.contains("LobbyManager.sync("));
    }

    @Test
    void applicationBoundaryContainsNoTransportRegistrationEventsOrMessages() throws IOException {
        String application = source("progression/ProgressApplicationService.java");

        assertFalse(application.contains("PacketHandler"));
        assertFalse(application.contains("registerMessage"));
        assertFalse(application.contains("@SubscribeEvent"));
        assertFalse(application.contains("sendSystemMessage"));
        assertFalse(application.contains("ProgressRepository"));
        assertFalse(application.contains("net.minecraft."));
        assertFalse(application.contains("net.minecraftforge."));
    }

    @Test
    void preparedPlanCannotRetainMutableAggregateOrCache() throws IOException {
        String plan = source("progression/PreparedProgressOperation.java");

        assertFalse(plan.contains("MutableProgressState"));
        assertFalse(plan.contains("PlayerProgressManager"));
        assertFalse(plan.contains("ServerPlayer"));
        assertFalse(plan.contains("Map<"));
        assertTrue(plan.contains("ProgressSnapshot snapshot"));
    }

    @Test
    void managerPreparesSnapshotBeforeExecutingResponseSaveAndSyncOutsideMonitor() throws IOException {
        String manager = source("progression/PlayerProgressManager.java");
        int publicFacade = manager.indexOf("public static PurchaseResult applyTabletClassPurchase(");
        int explicitLock = manager.indexOf("withProgressLock(", publicFacade);
        int postLockExecution = manager.indexOf("executePostLockEffects(", explicitLock);
        int snapshotPreparation = manager.indexOf("new QueuedProgressSave(snapshot(");
        int repositorySave = manager.indexOf("repository.save(preparedSnapshot, false)");

        assertTrue(publicFacade >= 0);
        assertTrue(explicitLock > publicFacade);
        assertTrue(postLockExecution > explicitLock);
        assertTrue(snapshotPreparation >= 0);
        assertTrue(repositorySave >= 0);
        assertFalse(manager.contains("public static synchronized PurchaseResult applyTabletClassPurchase("));
        assertFalse(manager.contains("public static synchronized ProgressionResult applyTabletBaseUnlock("));
        assertFalse(manager.contains("public static synchronized ProgressionResult applyTabletTierUpgrade("));
    }

    @Test
    void packetHandlerDoesNotDependOnPersistenceOrMutableProfile() throws IOException {
        String packet = source("tablet/net/TabletPacket.java");

        assertFalse(packet.contains("ProgressRepository"));
        assertFalse(packet.contains("MutableProgressState"));
        assertFalse(packet.contains("PlayerProgress "));
        assertFalse(packet.contains("PlayerProgressManager.savePlayer(player)"));
        assertTrue(packet.contains("PlayerProgressManager.applyTabletClassPurchase(player, kit, result ->"));
        assertTrue(packet.contains("PlayerProgressManager.applyTabletBaseUnlock(player, kit, result ->"));
        assertTrue(packet.contains("PlayerProgressManager.applyTabletTierUpgrade(player, kit, targetTier, result ->"));
    }

    @Test
    void senderRateLimitAndTabletValidationRemainBeforeApplicationOperations() throws IOException {
        String packet = source("tablet/net/TabletPacket.java");
        int sender = packet.indexOf("ServerPlayer player = ctx.get().getSender()");
        int rateLimit = packet.indexOf("PacketHandler.allowC2S(player, PacketHandler.C2SAction.TABLET)");
        int tablet = packet.indexOf("InventoryManager.hasTablet(player)");
        int firstApplication = packet.indexOf("PlayerProgressManager.applyTabletClassPurchase(player, kit, result ->");

        assertTrue(sender >= 0);
        assertTrue(rateLimit > sender);
        assertTrue(tablet > rateLimit);
        assertTrue(firstApplication > tablet);
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath)).replace("\r\n", "\n");
    }
}
