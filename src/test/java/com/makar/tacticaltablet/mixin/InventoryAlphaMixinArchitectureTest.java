package com.makar.tacticaltablet.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryAlphaMixinArchitectureTest {
    private static final Path MIXINS =
            Path.of("src/main/java/com/makar/tacticaltablet/mixin");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void allScreenPatchesAreClientOnlyAndCuriosIsOptional() throws IOException {
        String vanillaConfig = resource("tacticaltablet.mixins.json");
        String curiosConfig = resource("tacticaltablet.curios.mixins.json");
        String modsToml = resource("META-INF/mods.toml");

        assertTrue(vanillaConfig.contains("\"client\""));
        assertTrue(vanillaConfig.contains("InventoryScreenAlphaMixin"));
        assertTrue(curiosConfig.contains("\"required\": false"));
        assertTrue(curiosConfig.contains("\"client\""));
        assertTrue(curiosConfig.contains("CuriosMixinConfigPlugin"));
        assertFalse(curiosConfig.contains("\"mixins\""));

        int curiosDependency = modsToml.indexOf("modId=\"curios\"");
        assertTrue(curiosDependency >= 0);
        String curiosSection = modsToml.substring(curiosDependency);
        assertTrue(curiosSection.contains("mandatory=false"));
        assertTrue(curiosSection.contains("side=\"CLIENT\""));
    }

    @Test
    void curiosPluginChecksTheLoadingModListWithoutClientOrCuriosLinks() throws IOException {
        String plugin = source("CuriosMixinConfigPlugin.java");

        assertTrue(plugin.contains("FMLLoader.getLoadingModList()"));
        assertTrue(plugin.contains("getModFileById(\"curios\")"));
        assertFalse(plugin.contains("net.minecraft.client"));
        assertFalse(plugin.contains("top.theillusivec4.curios"));
    }

    @Test
    void mixinsScopeStandardAlphaBlendAwayFromThePlayerModel() throws IOException {
        String helper = Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/tablet/client/GuiTextureRenderer.java"
        ));
        assertTrue(helper.contains("RenderSystem.enableBlend();"));
        assertTrue(helper.contains("RenderSystem.defaultBlendFunc();"));
        assertTrue(helper.contains("graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);"));
        assertTrue(helper.contains("RenderSystem.disableBlend();"));

        assertCuriosScope(source("client/CuriosScreenAlphaMixin.java"));
        assertCuriosScope(source("client/CuriosScreenV2AlphaMixin.java"));

        String vanilla = source("client/InventoryScreenAlphaMixin.java");
        assertTrue(vanilla.contains("BACKGROUND_BLIT"));
        assertTrue(vanilla.contains("At.Shift.BEFORE"));
        assertTrue(vanilla.contains("At.Shift.AFTER"));
    }

    private static void assertCuriosScope(String source) {
        assertTrue(source.contains("@Pseudo"));
        assertTrue(source.contains("PLAYER_MODEL_RENDER"));
        assertTrue(source.contains("tacticaltablet$pauseBlendForPlayerModel"));
        assertTrue(source.contains("tacticaltablet$resumeBackgroundBlend"));
        assertTrue(source.contains("@At(\"RETURN\")"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(MIXINS.resolve(relativePath)).replace("\r\n", "\n");
    }

    private static String resource(String relativePath) throws IOException {
        return Files.readString(RESOURCES.resolve(relativePath)).replace("\r\n", "\n");
    }
}
