package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletUiArchitectureTest {
    private static final Path CLIENT = Path.of("src/main/java/com/makar/tacticaltablet/tablet/client");

    @Test
    void actionPagesUseRegistryAndHaveNoEightItemCopyLimit() throws IOException {
        String screen = source("TabletScreen.java");

        assertTrue(screen.contains("actionsFor(ClassCategory.BASE)"));
        assertTrue(screen.contains("actionsFor(ClassCategory.SHOP)"));
        assertTrue(screen.contains("actionsFor(ClassCategory.EXCLUSIVE)"));
        assertFalse(screen.contains("Arrays.copyOf"));
        int pagesStart = screen.indexOf("private static final TabletPage[] PAGES");
        int pagesEnd = screen.indexOf("};", pagesStart);
        assertFalse(screen.substring(pagesStart, pagesEnd).contains("TELEPORT_RTP"));
    }

    @Test
    void scrollGridIsWidgetFreeAndAlwaysReleasesScissor() throws IOException {
        String grid = source("ScrollableActionGrid.java");

        assertFalse(grid.contains("extends Button"));
        assertFalse(grid.contains("addRenderableWidget"));
        assertTrue(grid.contains("try {"));
        assertTrue(grid.contains("finally {"));
        assertTrue(grid.contains("graphics.disableScissor()"));
    }

    @Test
    void navigationAndCardsHaveDedicatedComponents() throws IOException {
        String screen = source("TabletScreen.java");

        assertTrue(screen.contains("TabletNavigationRail navigationRail"));
        assertTrue(screen.contains("TabletActionCard.render"));
        assertTrue(screen.contains("new TabletRtpButton"));
    }

    @Test
    void buttonBackgroundsUseMandatoryBlitsWithoutRuntimeTextureFallbacks() throws IOException {
        String card = source("TabletActionCard.java");
        String navigation = source("TabletNavigationRail.java");
        String screen = source("TabletScreen.java");
        String textures = source("TabletButtonTextures.java");

        String cardBackground = card.substring(card.indexOf("public static void render"),
                card.indexOf("private static void renderFallbackIcon"));
        assertTrue(cardBackground.contains("TabletButtonTextures.CLASS_BUTTON"));
        assertTrue(cardBackground.contains("GuiTextureRenderer.blitWithAlpha"));
        assertFalse(cardBackground.contains("graphics.fill"));

        assertTrue(navigation.contains("textures().select(true, selected, hovered)"));
        assertTrue(navigation.contains("GuiTextureRenderer.blitWithAlpha"));
        assertFalse(navigation.contains("graphics.fill"));

        assertBlitsWithoutFill(screen, "private class TabletRtpButton", "private void showPurchaseConfirmation");
        assertBlitsWithoutFill(screen, "private class ClanTextureButton", "private class ClanColorButton");
        assertBlitsWithoutFill(screen, "private class ClanColorButton", "private class ConfirmTextureButton");
        assertBlitsWithoutFill(screen, "private class ConfirmTextureButton", "private enum ConfirmButtonKind");

        assertFalse(textures.contains("OptionalGuiTextureResolver"));
        assertFalse(textures.contains("getResource("));
        assertFalse(textures.contains("ResourceManager"));
    }

    @Test
    void alphaRendererConfiguresBlendAndAlwaysRestoresRenderState() throws IOException {
        String renderer = source("GuiTextureRenderer.java");

        int enableBlend = renderer.indexOf("RenderSystem.enableBlend();");
        int defaultBlend = renderer.indexOf("RenderSystem.defaultBlendFunc();");
        int shader = renderer.indexOf("RenderSystem.setShader(GameRenderer::getPositionTexShader);");
        int texture = renderer.indexOf("RenderSystem.setShaderTexture(0, texture);");
        int color = renderer.indexOf("graphics.setColor(red, green, blue, alpha);");
        int blit = renderer.indexOf("graphics.blit(");
        int finallyBlock = renderer.indexOf("} finally {");
        int resetColor = renderer.indexOf("graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);");
        int disableBlend = renderer.indexOf("RenderSystem.disableBlend();");

        assertTrue(enableBlend >= 0);
        assertTrue(enableBlend < defaultBlend);
        assertTrue(defaultBlend < shader);
        assertTrue(shader < texture);
        assertTrue(texture < color);
        assertTrue(color < blit);
        assertTrue(blit < finallyBlock);
        assertTrue(finallyBlock < resetColor);
        assertTrue(resetColor < disableBlend);
    }

    @Test
    void tabletShellDoesNotCoverResourcePackTransparency() throws IOException {
        String screen = source("TabletScreen.java");
        String renderShell = screen.substring(
                screen.indexOf("private void renderShell"),
                screen.indexOf("private void renderFooter")
        );

        assertFalse(renderShell.contains(".fill("));
        assertTrue(renderShell.contains("drawHeader("));

        assertFalse(screen.contains("g.fill(0, 0, this.width, this.height, 0xAA000000);"));

        String voting = source("VotingScreen.java");
        assertFalse(voting.contains("g.fill(0, 0, this.width, this.height, 0x99000000);"));
        assertFalse(voting.contains("g.fill(x + 4, y + 4, x + PANEL_W + 4, y + PANEL_H + 4"));
    }

    @Test
    void classTintUsesCurrentTierAndPreservesUiHitboxDimensions() throws IOException {
        String screen = source("TabletScreen.java");
        String grid = source("ScrollableGridLayout.java");
        String navigation = source("TabletNavigationRail.java");

        String cardRenderer = screen.substring(
                screen.indexOf("private void renderActionCard"),
                screen.indexOf("private void pressAction")
        );
        assertTrue(cardRenderer.contains("action.fixedLevel()"));
        assertTrue(cardRenderer.contains("TabletClientState.getClassTier(action.classKey())"));
        assertFalse(cardRenderer.contains("getAvailableUpgradeTier"));

        assertTrue(screen.contains("private static final int UI_WIDTH = 380;"));
        assertTrue(screen.contains("private static final int UI_HEIGHT = 220;"));
        assertTrue(screen.contains("private static final int RTP_W = 78;"));
        assertTrue(screen.contains("private static final int RTP_H = 20;"));
        assertTrue(grid.contains("public static final int CARD_WIDTH = 130;"));
        assertTrue(grid.contains("public static final int CARD_HEIGHT = 34;"));
        assertTrue(navigation.contains("public static final int WIDTH = 72;"));
        assertTrue(navigation.contains("public static final int BUTTON_HEIGHT = 28;"));
    }

    private static void assertBlitsWithoutFill(String source, String startMarker, String endMarker) {
        String section = source.substring(source.indexOf(startMarker), source.indexOf(endMarker));
        assertTrue(section.contains("GuiTextureRenderer.blitWithAlpha"), startMarker);
        assertFalse(section.contains(".fill("), startMarker);
    }

    private static String source(String name) throws IOException {
        return Files.readString(CLIENT.resolve(name)).replace("\r\n", "\n");
    }
}
