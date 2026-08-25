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
    void vanillaTitleAndPauseMenusAreReplacedClientSideWithoutMixins() throws IOException {
        String events = read(Path.of(
                "src/main/java/com/makar/tacticaltablet/client/event/ClientScreenEvents.java"));

        assertTrue(events.contains("value = Dist.CLIENT"));
        assertTrue(events.contains("ScreenEvent.Opening"));
        assertTrue(events.contains("newScreen instanceof TitleScreen"));
        assertTrue(events.contains("event.setNewScreen(new CustomMainMenu())"));
        assertTrue(events.contains("newScreen instanceof PauseScreen"));
        assertTrue(events.contains("VANILLA_PAUSE_MENU_TITLE.equals(newScreen.getTitle())"));
        assertTrue(events.contains("event.setNewScreen(new CustomPauseScreen())"));
        assertFalse(events.contains("@Mixin"));
    }

    @Test
    void playConnectsDirectlyToTheOnlyServerAndUsesTheMenuAsParent() throws IOException {
        String menu = read(CLIENT_GUI.resolve("CustomMainMenu.java"));

        assertTrue(menu.contains("deluxewarfare.sosal.today"));
        assertFalse(menu.contains("zuma.sos-al.net"));
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
        int guide = menu.indexOf("main_menu.guide");
        int settings = menu.indexOf("main_menu.settings");
        int quit = menu.indexOf("main_menu.quit");

        assertTrue(play >= 0);
        assertTrue(play < guide);
        assertTrue(guide < settings);
        assertTrue(settings < quit);
        assertFalse(menu.contains("main_menu.rules"));
        assertTrue(Files.isRegularFile(CLIENT_GUI.resolve("RulesScreen.java")));
    }

    @Test
    void mainMenuUsesTheProvidedTextureSetWithoutDuplicateMinecraftLabels() throws IOException {
        String menu = read(CLIENT_GUI.resolve("CustomMainMenu.java"));
        String textureSet = read(CLIENT_GUI.resolve("MenuTextureSet.java"));
        String textureButton = read(CLIENT_GUI.resolve("component/TextureMenuButton.java"));
        String tabletRenderer = read(CLIENT_GUI.resolve("render/TabletMenuRenderer.java"));

        assertTrue(menu.contains("MenuTextureSet.BACKGROUND"));
        assertTrue(menu.contains("MenuTextureSet.TABLET"));
        assertTrue(menu.contains("MenuTextureSet.JOIN"));
        assertTrue(menu.contains("MenuTextureSet.INFO"));
        assertTrue(menu.contains("MenuTextureSet.SETTINGS"));
        assertTrue(menu.contains("MenuTextureSet.EXIT"));
        assertTrue(menu.contains("ENTRANCE_DURATION_SECONDS = 0.34F"));
        assertTrue(menu.contains("TabletMenuLayout.calculateMain(width, height)"));
        assertTrue(menu.contains("TabletMenuRenderer.render("));
        assertTrue(menu.contains("setRevealProgress(buttonProgress"));
        assertFalse(menu.contains("Button.builder"));
        assertFalse(menu.contains("drawString"));

        assertTrue(textureSet.contains("menu.png"));
        assertTrue(textureSet.contains("tablet.png"));
        assertTrue(textureSet.contains("tablet_pause.png"));
        assertTrue(textureButton.contains("class TextureMenuButton extends Button"));
        assertTrue(textureButton.contains("GuiTextureRenderer.blitRegionWithAlpha"));
        assertTrue(textureButton.contains("HOVER_DURATION_SECONDS = 0.14F"));
        assertTrue(tabletRenderer.contains("START_SCALE = 0.92F"));
        assertTrue(tabletRenderer.contains("GuiTextureRenderer.blitRegionWithAlpha"));
    }

    @Test
    void pauseMenuAnimatesTheSharedVisualSystemAndDisconnectsBackToCustomMenu() throws IOException {
        String pause = read(CLIENT_GUI.resolve("CustomPauseScreen.java"));

        int resume = pause.indexOf("pause.resume");
        int guide = pause.indexOf("main_menu.guide");
        int settings = pause.indexOf("main_menu.settings");
        int quit = pause.indexOf("pause.quit");

        assertTrue(resume >= 0);
        assertTrue(resume < guide);
        assertTrue(guide < settings);
        assertTrue(settings < quit);
        assertTrue(pause.contains("new AnimatedFloat(0.0F, ENTRANCE_DURATION_SECONDS)"));
        assertTrue(pause.contains("TabletMenuLayout.calculatePause(width, height)"));
        assertTrue(pause.contains("TabletMenuRenderer.render("));
        assertTrue(pause.contains("MenuTextureSet.PAUSE_TABLET"));
        assertTrue(pause.contains("MenuTextureSet.CONTINUE"));
        assertFalse(pause.contains("MenuTextureSet.JOIN"));
        assertFalse(pause.contains("renderBackground"));
        assertFalse(pause.contains("graphics.fill"));
        assertTrue(pause.contains("minecraft.setScreen(null)"));
        assertTrue(pause.contains("minecraft.level.disconnect()"));
        assertTrue(pause.contains("minecraft.clearLevel()"));
        assertTrue(pause.contains("minecraft.setScreen(new CustomMainMenu())"));
        assertTrue(pause.contains("public boolean isPauseScreen()"));
    }

    @Test
    void providedMenuTextureSetIsPackagedAsProjectResources() throws IOException {
        Path root = Path.of(
                "src/main/resources/assets/tacticaltablet/textures/gui/main_menu");
        for (String fileName : new String[]{
                "menu.png",
                "tablet.png",
                "tablet_pause.png",
                "button_join.png",
                "button_continue.png",
                "button_info.png",
                "button_settings.png",
                "button_exit.png"
        }) {
            Path texture = root.resolve(fileName);
            assertTrue(Files.isRegularFile(texture), fileName);
            assertTrue(Files.size(texture) > 0L, fileName);
        }
    }

    @Test
    void settingsExposeFovSoundsEmbeddiumGraphicsControlsAndBack() throws IOException {
        String settings = read(CLIENT_GUI.resolve("CustomSettingsScreen.java"));
        String graphicsFactory = read(CLIENT_GUI.resolve("GraphicsSettingsScreenFactory.java"));

        assertTrue(settings.contains("new TacticalSlider("));
        assertTrue(settings.contains("GraphicsSettingsScreenFactory.create(this, minecraft.options)"));
        assertTrue(settings.contains("new CustomSoundSettingsScreen(this, minecraft.options)"));
        assertTrue(settings.contains("new CustomControlsScreen(this, minecraft.options)"));
        assertTrue(settings.contains("Minecraft.getInstance().options.save()"));
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
