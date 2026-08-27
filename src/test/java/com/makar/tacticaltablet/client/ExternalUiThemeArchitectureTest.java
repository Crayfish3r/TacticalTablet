package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalUiThemeArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void externalPaletteUsesRequestedColorsAndTabletThemeRemainsUnchanged() {
        assertEquals(0xFF246B25, ExternalUiTheme.PRIMARY);
        assertEquals(0xFF6B246A, ExternalUiTheme.SECONDARY);
        assertEquals(0xFF59E0B7, TacticalTheme.ACCENT);
        assertEquals(0xFF3A8F7A, TacticalTheme.ACCENT_MUTED);
    }

    @Test
    void nonTabletScreensUseScopedPaletteWhileTabletScreensKeepDefaultScope() throws IOException {
        String mainMenu = source("client/gui/CustomMainMenu.java");
        String settings = source("client/gui/CustomSettingsScreen.java");
        String tablet = source("tablet/client/TabletScreen.java");

        assertTrue(mainMenu.contains("openFrame(frame, ExternalUiTheme.PALETTE)"));
        assertTrue(settings.contains("ExternalUiTheme.PALETTE"));
        assertFalse(tablet.contains("ExternalUiTheme"));
        assertTrue(tablet.contains("TacticalUi.openFrame("));
    }

    @Test
    void staminaEditorHasLivePreviewThinBarsAndNoLetterLabels() throws IOException {
        String overlay = source("integration/moderndamage/client/ModernDamageStaminaOverlay.java");
        String editor = source("integration/moderndamage/client/ModernDamageHudEditorScreen.java");
        String settings = source("integration/moderndamage/client/ModernDamageSettingsScreen.java");

        assertTrue(overlay.contains("trackHeight = 4"));
        assertFalse(overlay.contains("renderArmIcon"));
        assertFalse(overlay.contains("renderLegIcon"));
        assertFalse(overlay.contains("ICON_WIDTH"));
        assertTrue(overlay.contains("trackX = 0"));
        assertTrue(overlay.contains("renderPreview"));
        assertFalse(overlay.contains("hud.tacticaltablet.mdc.arms"));
        assertFalse(overlay.contains("hud.tacticaltablet.mdc.legs"));
        assertTrue(editor.contains("ModernDamageStaminaOverlay.renderPreview"));
        assertTrue(editor.contains("TacticalScreenBackground.renderBase"));
        assertTrue(editor.contains("List.of(layout.panelRect())"));
        assertTrue(settings.contains("new ModernDamageHudEditorScreen(this)"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative)).replace("\r\n", "\n");
    }
}
