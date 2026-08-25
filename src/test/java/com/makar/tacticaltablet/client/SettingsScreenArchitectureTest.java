package com.makar.tacticaltablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsScreenArchitectureTest {

    private static final Path GUI = Path.of(
            "src/main/java/com/makar/tacticaltablet/client/gui");

    @Test
    void settingsKeepGraphicsFlowAndUseTacticalScreens() throws IOException {
        String settings = read("CustomSettingsScreen.java");
        assertTrue(settings.contains("GraphicsSettingsScreenFactory.create(this"));
        assertTrue(settings.contains("new CustomSoundSettingsScreen(this"));
        assertTrue(settings.contains("new CustomControlsScreen(this"));
        assertTrue(settings.contains("new ModListScreen(this)"));
        assertTrue(settings.contains("VIEWMODEL_TUNER_MOD_ID = \"viewmodel_tuner\""));
        assertTrue(settings.contains("ForgeModConfigScreenFactory.create("));
        String modConfigFactory = read("ForgeModConfigScreenFactory.java");
        assertTrue(modConfigFactory.contains("ConfigScreenHandler.getScreenFactoryFor"));
        assertTrue(settings.contains("new TacticalSlider("));
        assertTrue(settings.contains("TacticalUi.drawPanel("));
    }

    @Test
    void controlsOmitOperatorOptionAndOpenFilteredBindings() throws IOException {
        String controls = read("CustomControlsScreen.java");
        assertTrue(controls.contains("new FilteredKeyBindsScreen(this"));
        assertTrue(controls.contains("new MouseSettingsScreen(this"));
        assertFalse(controls.contains("operatorItemsTab"));
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(GUI.resolve(fileName)).replace("\r\n", "\n");
    }
}
