package com.makar.tacticaltablet.casino;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
class CasinoMachineArchitectureTest {
    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/makar/tacticaltablet", file));
    }
    @Test void blockMenuAndSpectatorUseSameServerLifecycle() throws Exception {
        assertTrue(source("casino/CasinoMachineBlock.java").contains("CasinoSessionManager.openFromMachine(serverPlayer, pos)"));
        assertTrue(source("casino/CasinoMenu.java").contains("CasinoSessionManager.close(serverPlayer, sessionId)"));
        String screen = source("client/casino/CasinoScreen.java");
        assertTrue(screen.contains("AbstractContainerScreen<CasinoMenu>"));
        assertTrue(screen.contains("switchView(View.ODDS)"));
        assertFalse(screen.contains("setScreen("));
        assertTrue(source("casino/CasinoSessionManager.java").contains("open(player, Source.SPECTATOR, null)"));
        assertFalse(source("casino/net/CasinoClosePacket.java").contains("allowC2S"));
    }
    @Test void spinValidatesMachineThenCommitsBeforeStartingVisuals() throws Exception {
        String session = source("casino/CasinoSessionManager.java");
        assertTrue(session.indexOf("!isMenuValid(player, menu)") < session.indexOf("SPIN_TABLE.roll(stake)"));
        assertTrue(session.indexOf("!machine.reserveSpin()") < session.indexOf("SPIN_TABLE.roll(stake)"));
        assertTrue(session.indexOf("PlayerProgressManager.applyCasinoSpin") < session.indexOf("machine.startAnimation(seed, reels)"));
        assertTrue(session.contains("actual == session.machine"));
        assertTrue(session.contains("if (machine != null && newlyApplied)"));
        assertTrue(session.contains("requestId.equals(session.lastRequestId)"));
        String request = source("casino/net/CasinoSpinRequestPacket.java");
        assertFalse(request.contains("readBlockPos")); assertFalse(request.contains("readResourceLocation"));
    }
    @Test void suppliedObjAndSeamlessTextureAreUnmodified() throws Exception {
        assertHash("models/block/slot_machine_casino.obj", "851a15a712a9d63df25dbdbb6c166e3c366d107b67d1362627ef2e9e8e3a4549");
        assertHash("textures/block/slot_machine_casino_seamless_texture_2048.png", "afc86053e52fde72a886a826f8d3d41f0bda45a3f07ee5c908ad4130a5f0d805");
    }
    private static void assertHash(String path, String expected) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("src/main/resources/assets/tacticaltablet", path));
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
}
