package com.makar.tacticaltablet.camouflage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CamouflageArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void successfulClassSelectionTriggersDeploymentAfterCommittedState() throws Exception {
        String source = read("tablet/net/TabletPacket.java");
        String normal = section(source, "private void handleKit", "private void handleChaosKit");
        String chaos = section(source, "private void handleChaosKit", "private String getDisplayName");

        assertSuccessfulSelectionOrder(normal, "KitManager.giveKit(player, kit)", false);
        assertSuccessfulSelectionOrder(
                chaos,
                "KitManager.giveKit(player, kit, ChaosSetManager.tierFor(player, kit))",
                true
        );
    }

    @Test
    void failedClassSelectionCannotTriggerDeployment() throws Exception {
        String source = read("tablet/net/TabletPacket.java");
        String normal = section(source, "private void handleKit", "private void handleChaosKit");
        String chaos = section(source, "private void handleChaosKit", "private String getDisplayName");

        assertFailureReturnsBeforeDeployment(normal, "if (!KitManager.giveKit(player, kit))");
        assertFailureReturnsBeforeDeployment(
                chaos,
                "if (!KitManager.giveKit(player, kit, ChaosSetManager.tierFor(player, kit)))"
        );
    }

    @Test
    void classBeforeRtpDeliversWithoutRtpOnlyEligibility() throws Exception {
        String service = read("camouflage/CamouflageLoadoutService.java");
        assertTrue(service.contains("MatchAdmissionManager.isCurrentMatchParticipant(player.getUUID())"));
        assertTrue(service.contains("PlayerTabletState.isKitUsed(player)"));
        assertTrue(service.contains("LivesManager.canContinueMatch(player)"));
        assertFalse(service.contains("LivesManager.isAliveParticipant(player)"));
        assertFalse(service.contains("PlayerTabletState.isRtpUsed(player)"));
        assertFalse(service.contains("war.playing"));
    }

    @Test
    void rtpBeforeClassDoesNotDeliverUntilSuccessfulSelection() throws Exception {
        String rtp = read("game/respawn/RtpTimerManager.java");
        assertFalse(rtp.contains("CamouflageLoadoutService"));

        String packet = read("tablet/net/TabletPacket.java");
        String normal = section(packet, "private void handleKit", "private void handleChaosKit");
        int kitUsed = normal.indexOf("PlayerTabletState.setKitUsed(player)");
        int camouflage = normal.indexOf("CamouflageLoadoutService.deploy(player)", kitUsed);
        int rtpStateBranch = normal.indexOf("if (PlayerTabletState.isRtpUsed(player))", camouflage);
        assertTrue(kitUsed >= 0 && camouflage > kitUsed && rtpStateBranch > camouflage);
    }

    @Test
    void rtpCompletionCannotCreateSecondCamouflageKit() throws Exception {
        String rtp = read("game/respawn/RtpTimerManager.java");
        assertFalse(rtp.contains("CamouflageLoadoutService"));

        String packet = read("tablet/net/TabletPacket.java");
        assertEquals(2, countOccurrences(packet, "CamouflageLoadoutService.deploy(player)"));
    }

    @Test
    void deathCleanupPrecedesCorpseCaptureAndLogoutUsesSameHelper() throws Exception {
        String events = read("game/ServerEvents.java");
        int death = events.indexOf("CamouflageLoadoutService.clearTemporary(victim)");
        int corpse = events.indexOf("CorpseLootManager.createCorpse(victim)", death);
        assertTrue(death >= 0 && death < corpse);
        assertTrue(events.contains("CamouflageLoadoutService.clearTemporary(player)"));

        String lifecycle = read("game/lifecycle/PlayerLifecycleSanitizer.java");
        assertTrue(lifecycle.contains("CamouflageLoadoutService.clearTemporary(player)"));
        String lobby = read("game/lobby/LobbyManager.java");
        assertTrue(lobby.contains("PlayerLifecycleSanitizer.clearPreviousLifeState(player)"));
    }

    @Test
    void cleanupIsMarkerOnlyAndHotPathDoesNoFileIoOrWorldDrop() throws Exception {
        String service = read("camouflage/CamouflageLoadoutService.java");
        assertTrue(service.contains("CamouflageStackMarker.isAutoCamouflage(stack)"));
        assertTrue(service.contains("CuriosInventoryBridge.removeMatching"));
        int reconcile = service.indexOf("clearTemporary(player)");
        int issuance = service.indexOf("for (CamouflagePiece piece", reconcile);
        assertTrue(reconcile >= 0 && issuance > reconcile);
        assertFalse(service.contains("Files."));
        assertFalse(service.contains("drop("));
    }

    @Test
    void markedTossIsCancelledByExistingInventoryGuard() throws Exception {
        String events = read("inventory/InventoryLockEvents.java");
        int marker = events.indexOf("CamouflageStackMarker.isAutoCamouflage");
        int cancellation = events.indexOf("event.setCanceled(true)", marker);
        assertTrue(marker >= 0 && cancellation > marker);
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static void assertSuccessfulSelectionOrder(String source, String giveKitCall, boolean chaos) {
        int giveKit = source.indexOf(giveKitCall);
        int chaosSelection = chaos ? source.indexOf("ChaosSetManager.select(player, kit)", giveKit) : giveKit;
        int selectedClass = source.indexOf("PlayerTabletState.setSelectedClass(player, kit)", chaosSelection);
        int kitUsed = source.indexOf("PlayerTabletState.setKitUsed(player)", selectedClass);
        int camouflage = source.indexOf("CamouflageLoadoutService.deploy(player)", kitUsed);
        assertTrue(giveKit >= 0
                && chaosSelection >= giveKit
                && selectedClass > chaosSelection
                && kitUsed > selectedClass
                && camouflage > kitUsed);
    }

    private static void assertFailureReturnsBeforeDeployment(String source, String failureCondition) {
        int failure = source.indexOf(failureCondition);
        int failureReturn = source.indexOf("return;", failure);
        int camouflage = source.indexOf("CamouflageLoadoutService.deploy(player)", failureReturn);
        assertTrue(failure >= 0 && failureReturn > failure && camouflage > failureReturn);
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
