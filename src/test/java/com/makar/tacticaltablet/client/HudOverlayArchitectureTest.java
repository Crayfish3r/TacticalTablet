package com.makar.tacticaltablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudOverlayArchitectureTest {

    @Test
    void overlaysUseSharedAnchorsAndKeepExistingVisibilityRules() throws IOException {
        String lives = read("LivesHudOverlay.java");
        String airdrop = read("AirdropNoticeOverlay.java");
        String spectator = read("SpectatorCameraClientEvents.java");

        assertTrue(lives.contains("private static final int HOTBAR_WIDTH = 182;"));
        assertTrue(lives.contains("\"X\" + alivePlayers + \" (\" + remainingLivesTotal + \")\""));
        assertFalse(lives.contains("HudAnchorManager.hotbarSide("));
        assertFalse(lives.contains("TacticalUi.drawCutCornerBorder"));
        assertTrue(lives.contains("player == null || player.isSpectator() || minecraft.options.hideGui"));
        assertTrue(airdrop.contains("HudAnchorManager.topCenter("));
        assertTrue(airdrop.contains("minecraft.player == null || minecraft.options.hideGui"));
        assertTrue(airdrop.contains("wrapped.subList(0, Math.min(2, wrapped.size()))"));
        assertTrue(spectator.contains("HudAnchorManager.spectatorHint("));
        assertTrue(spectator.contains("if (minecraft.options.hideGui) return;"));
    }

    @Test
    void deathTransitionWrapsContentAndHonorsReducedMotion() throws IOException {
        String death = read("DeathScreenOverlay.java");

        assertTrue(death.contains("limitedLines(font.split(Component.literal(titleText), titleWidth), 2)"));
        assertTrue(death.contains("limitedLines(font.split(Component.literal(subtitleText), subtitleWidth), 3)"));
        assertTrue(death.contains("screenEffectScale().get() <= 0.0D"));
        assertTrue(death.contains("super(narrationTitle(title, subtitle));"));
        assertTrue(death.contains("public boolean shouldCloseOnEsc() {\n        return false;"));
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(Path.of("src/main/java/com/makar/tacticaltablet/client", fileName))
                .replace("\r\n", "\n");
    }
}
