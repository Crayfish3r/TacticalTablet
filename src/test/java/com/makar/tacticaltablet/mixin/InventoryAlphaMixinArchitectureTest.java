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
        assertTrue(vanillaConfig.contains("\"required\": false"));
        assertTrue(vanillaConfig.contains("\"defaultRequire\": 0"));
        assertTrue(vanillaConfig.contains("InventoryScreenAlphaMixin"));
        assertTrue(curiosConfig.contains("\"required\": false"));
        assertTrue(curiosConfig.contains("\"defaultRequire\": 0"));
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
    void mixinsWrapOnlyTextureBlitsInExceptionSafeNonFatalScopes() throws IOException {
        String helper = Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/tablet/client/GuiTextureRenderer.java"
        ));
        assertTrue(helper.contains("RenderSystem.enableBlend();"));
        assertTrue(helper.contains("RenderSystem.defaultBlendFunc();"));
        assertTrue(helper.contains("graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);"));
        assertTrue(helper.contains("BlendState.capture()"));
        assertTrue(helper.contains("previous.restore()"));
        assertTrue(helper.contains("if (enabled)"));
        assertTrue(helper.contains("RenderSystem.disableBlend();"));
        assertTrue(helper.contains("try {"));
        assertTrue(helper.contains("} finally {"));
        String implicitPath = helper.substring(
                helper.indexOf("private static void withImplicitAlphaBlend"),
                helper.indexOf("public static void withAlphaBlend")
        );
        assertTrue(implicitPath.contains("openAlphaBlend(graphics)"));
        assertFalse(implicitPath.contains("RenderSystem.disableBlend()"));

        assertAtomicBlitScope(source("client/CuriosScreenAlphaMixin.java"));
        assertAtomicBlitScope(source("client/CuriosScreenV2AlphaMixin.java"));

        String vanilla = source("client/InventoryScreenAlphaMixin.java");
        assertTrue(vanilla.contains("BACKGROUND_BLIT"));
        assertAtomicBlitScope(vanilla);
    }

    private static void assertAtomicBlitScope(String source) {
        assertTrue(source.contains("@Redirect"));
        assertTrue(source.contains("require = 0"));
        assertTrue(source.contains("GuiTextureRenderer.withAlphaBlend"));
        assertFalse(source.contains("renderEntityInInventoryFollowsMouse"));
        assertFalse(source.contains("@At(\"HEAD\")"));
        assertFalse(source.contains("@At(\"RETURN\")"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(MIXINS.resolve(relativePath)).replace("\r\n", "\n");
    }

    private static String resource(String relativePath) throws IOException {
        return Files.readString(RESOURCES.resolve(relativePath)).replace("\r\n", "\n");
    }
}
