package com.makar.tacticaltablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomMainMenuArchitectureTest {

    private static final Path CLIENT_GUI =
            Path.of("src/main/java/com/makar/tacticaltablet/client/gui");

    @Test
    void titleScreenReplacementIsClientOnlyAndTargetsOnlyTheVanillaTitleScreen() throws IOException {
        String events = read(Path.of(
                "src/main/java/com/makar/tacticaltablet/client/event/ClientScreenEvents.java"));

        assertTrue(events.contains("value = Dist.CLIENT"));
        assertTrue(events.contains("ScreenEvent.Opening"));
        assertTrue(events.contains("event.getNewScreen() instanceof TitleScreen"));
        assertTrue(events.contains("event.setNewScreen(new CustomMainMenu())"));
        assertFalse(events.contains("@Mixin"));
    }

    @Test
    void playConnectsDirectlyToTheOnlyServerAndUsesTheMenuAsParent() throws IOException {
        String menu = read(CLIENT_GUI.resolve("CustomMainMenu.java"));

        assertTrue(menu.contains("deluxewarfare.sosal.today"));
        assertTrue(menu.contains("ConnectScreen.startConnecting(\n                this,"));
        assertTrue(menu.contains("ServerAddress.parseString(SERVER_ADDRESS)"));
        assertFalse(menu.contains("JoinMultiplayerScreen"));
        assertFalse(menu.contains("DirectJoinServerScreen"));
        assertFalse(menu.contains("OptionsScreen"));
    }

    @Test
    void mainMenuKeepsTheRequiredButtonOrder() throws IOException {
        String menu = read(CLIENT_GUI.resolve("CustomMainMenu.java"));

        int play = menu.indexOf("main_menu.play");
        int rules = menu.indexOf("main_menu.rules");
        int guide = menu.indexOf("main_menu.guide");
        int settings = menu.indexOf("main_menu.settings");
        int quit = menu.indexOf("main_menu.quit");

        assertTrue(play >= 0);
        assertTrue(play < rules);
        assertTrue(rules < guide);
        assertTrue(guide < settings);
        assertTrue(settings < quit);
    }

    @Test
    void mainMenuUsesGlassWidgetsAndStableParallaxBackground() throws IOException {
        String menu = read(CLIENT_GUI.resolve("CustomMainMenu.java"));
        String glassButton = read(CLIENT_GUI.resolve("component/GlassButton.java"));
        String renderUtils = read(CLIENT_GUI.resolve("render/GuiRenderUtils.java"));

        assertTrue(menu.contains("background_blurred.png"));
        assertTrue(menu.contains("BACKGROUND_ZOOM = 1.05F"));
        assertTrue(menu.contains("PARALLAX_X = 6.0F"));
        assertTrue(menu.contains("PARALLAX_Y = 4.0F"));
        assertTrue(menu.contains("Math.exp(-PARALLAX_RESPONSE * frame.deltaSeconds())"));
        assertTrue(menu.contains("GlassButton.ButtonStyle.PLAY"));
        assertTrue(menu.contains("GlassButton.ButtonStyle.DANGER"));
        assertFalse(menu.contains("Button.builder"));

        assertTrue(glassButton.contains("class GlassButton extends Button"));
        assertTrue(glassButton.contains("new AnimatedFloat(0.0F, HOVER_DURATION_SECONDS)"));
        assertTrue(glassButton.contains("HOVER_DURATION_SECONDS = 0.16F"));
        assertTrue(glassButton.contains("protected void renderWidget"));
        assertTrue(renderUtils.contains("fillRoundedRect"));
        assertTrue(renderUtils.contains("drawRoundedBorder"));
    }

    @Test
    void backgroundAssetsAreProjectResourcesWithMatchingDimensions() throws IOException {
        Path background = Path.of(
                "src/main/resources/assets/tacticaltablet/textures/gui/main_menu/background.png");
        Path blurred = Path.of(
                "src/main/resources/assets/tacticaltablet/textures/gui/main_menu/background_blurred.png");

        assertTrue(Files.isRegularFile(background));
        assertTrue(Files.isRegularFile(blurred));
        assertTrue(Files.size(background) > 0L);
        assertTrue(Files.size(blurred) > 0L);
    }

    @Test
    void settingsExposeOnlyEmbeddiumGraphicsControlsAndBack() throws IOException {
        String settings = read(CLIENT_GUI.resolve("CustomSettingsScreen.java"));
        String graphicsFactory = read(CLIENT_GUI.resolve("GraphicsSettingsScreenFactory.java"));

        assertTrue(settings.contains("GraphicsSettingsScreenFactory.create(this, minecraft.options)"));
        assertTrue(settings.contains("new ControlsScreen(this, minecraft.options)"));
        assertFalse(settings.contains("new OptionsScreen"));
        assertTrue(graphicsFactory.contains("me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI"));
        assertTrue(graphicsFactory.contains("screenClass.getConstructor(Screen.class)"));
        assertTrue(graphicsFactory.contains("new VideoSettingsScreen(parent, options)"));
    }

    @Test
    void childScreensReturnToTheirProvidedCustomParent() throws IOException {
        for (String fileName : new String[]{"RulesScreen.java", "GuideScreen.java", "CustomSettingsScreen.java"}) {
            String screen = read(CLIENT_GUI.resolve(fileName));
            assertTrue(screen.contains("private final Screen parent;"));
            assertTrue(screen.contains("Minecraft.getInstance().setScreen(parent);"));
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }
}
