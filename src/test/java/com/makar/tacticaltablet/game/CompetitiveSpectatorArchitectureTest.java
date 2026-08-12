package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompetitiveSpectatorArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void directTabletPacketRejectsVipBeforeKitAndStateMutation() throws IOException {
        String source = read("tablet/net/TabletPacket.java");
        int policy = source.indexOf("CompetitiveClassPolicy.isVipBlocked");
        assertTrue(policy > source.indexOf("private void handleKit"));
        assertTrue(policy < source.indexOf("KitManager.giveKit(player, kit)"));
        assertTrue(policy < source.indexOf("PlayerTabletState.setSelectedClass(player, kit)"));
    }

    @Test
    void targetChangesAndClearPathsSynchronizeHud() throws IOException {
        String camera = read("game/SpectatorCameraManager.java");
        assertTrue(camera.contains("sendHudStateIfChanged(player, target)"));
        assertTrue(camera.contains("onTargetClassChanged(ServerPlayer target)"));
        assertTrue(camera.contains("SpectatorHudStatePacket.clear()"));
        assertTrue(camera.contains("retargetViewersOf(player.server, player.getUUID())"));

        String client = read("client/SpectatorCameraClientEvents.java");
        assertTrue(client.contains("TabletClientState.isCompetitiveSet()"));
        assertTrue(client.contains("SpectatorHudClientState.snapshot()"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(MAIN.resolve(path)).replace("\r\n", "\n");
    }
}
