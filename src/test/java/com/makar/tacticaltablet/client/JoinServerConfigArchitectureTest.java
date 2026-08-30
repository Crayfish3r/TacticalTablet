package com.makar.tacticaltablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinServerConfigArchitectureTest {

    @Test
    void modsScreenExposesGreenClientOnlyAddressEditorWithoutChangingSettingsLayout() throws IOException {
        String registration = read("src/main/java/com/makar/tacticaltablet/client/ClientConfigScreenRegistration.java");
        String screen = read("src/main/java/com/makar/tacticaltablet/client/gui/JoinServerConfigScreen.java");
        String mod = read("src/main/java/com/makar/tacticaltablet/core/TacticalTabletMod.java");
        String settings = read("src/main/java/com/makar/tacticaltablet/client/gui/CustomSettingsScreen.java");

        assertTrue(registration.contains("ConfigScreenHandler.ConfigScreenFactory"));
        assertTrue(registration.contains("JoinServerConfigScreen::new"));
        assertTrue(mod.contains("DistExecutor.safeRunWhenOn(Dist.CLIENT"));
        assertTrue(screen.contains("ExternalUiTheme.PALETTE"));
        assertTrue(screen.contains("implements UiPaletteProvider"));
        assertTrue(settings.contains("PANEL_WIDTH = 520"));
        assertTrue(settings.contains("PANEL_HEIGHT = 260"));
        assertTrue(settings.contains("new ModListScreen(this)"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }
}
