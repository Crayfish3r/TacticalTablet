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
        assertTrue(settings.contains("ReplayModClientAdapter.isInstalled()"));
        assertTrue(settings.contains("\"screen.tacticaltablet.settings.replays\""));
        assertTrue(settings.contains("ReplayModClientAdapter.openViewer()"));
        String replayAdapter = Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/integration/replaymod/client/ReplayModClientAdapter.java"));
        int loadedCheck = replayAdapter.indexOf("if (!isInstalled()) return false;");
        int classLoad = replayAdapter.indexOf("Class.forName(REPLAY_MODULE_CLASS");
        assertTrue(loadedCheck >= 0 && loadedCheck < classLoad);
        assertTrue(replayAdapter.contains("VERIFIED_VERSION = \"1.20.1-2.6.13\""));
        assertFalse(replayAdapter.contains("import com.replaymod."));
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
