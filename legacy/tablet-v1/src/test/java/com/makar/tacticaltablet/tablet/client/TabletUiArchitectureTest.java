package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    void scrollGridUsesFocusableWidgetsAndScopedClipping() throws IOException {
        String grid = source("ScrollableActionGrid.java");

        assertTrue(grid.contains("extends Button implements FocusKeyProvider"));
        assertTrue(grid.contains("Consumer<Button> widgetRegistrar"));
        assertTrue(grid.contains("ScissorScope.open"));
        assertTrue(grid.contains("ScrollableGridLayout.scrollRowsToReveal"));
        assertFalse(grid.contains("graphics.disableScissor()"));
        assertFalse(grid.contains("public boolean mouseClicked"));
    }

    @Test
    void navigationAndCardsHaveDedicatedComponents() throws IOException {
        String screen = source("TabletScreen.java");

        assertTrue(screen.contains("TabletNavigationRail navigationRail"));
        assertTrue(screen.contains("TabletActionCard.render"));
        assertTrue(screen.contains("new TabletRtpButton"));
    }

    @Test
    void legacyButtonsKeepMandatoryBlitsWhileTacticalButtonIsProgrammatic() throws IOException {
        String card = source("TabletActionCard.java");
        String navigation = source("TabletNavigationRail.java");
        String screen = source("TabletScreen.java");
        String textures = source("TabletButtonTextures.java");
        String tacticalButton = Files.readString(CLIENT.resolve("ui/widget/TacticalButton.java"));

        String cardBackground = card.substring(card.indexOf("public static void render"),
                card.indexOf("private static void renderFallbackIcon"));
        assertTrue(cardBackground.contains("TabletButtonTextures.CLASS_BUTTON"));
        assertTrue(cardBackground.contains("GuiTextureRenderer.blitWithAlpha"));
        assertFalse(cardBackground.contains("graphics.fill"));

        assertTrue(navigation.contains("extends Button implements FocusKeyProvider"));
        assertTrue(navigation.contains("item.textures().select(active, selected, hovered || isFocused())"));
        assertTrue(navigation.contains("GuiTextureRenderer.blitWithAlpha"));
        assertFalse(navigation.contains("graphics.fill"));

        String rtp = screen.substring(screen.indexOf("private class TabletRtpButton"),
                screen.indexOf("private void showPurchaseConfirmation"));
        assertTrue(rtp.contains("extends TacticalButton"));
        assertFalse(rtp.contains("TabletButtonTextures.RTP"));
        assertFalse(rtp.contains("GuiTextureRenderer.blitWithAlpha"));
        assertTrue(tacticalButton.contains("TacticalUi.drawButton"));
        assertFalse(tacticalButton.contains("graphics.blit"));
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

        int color = renderer.indexOf("graphics.setColor(red, green, blue, alpha);");
        int blit = renderer.indexOf("graphics.blit(");
        int finallyBlock = renderer.indexOf("} finally {");
        int resetColor = renderer.indexOf("graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);");

        assertTrue(renderer.contains("withImplicitAlphaBlend(graphics, () ->"));
        assertTrue(renderer.contains("try (AlphaBlendScope ignored = openAlphaBlend(graphics))"));
        assertTrue(renderer.contains("BLEND_STATES.get().push(BlendState.capture())"));
        assertTrue(renderer.contains("RenderSystem.defaultBlendFunc();"));
        assertTrue(renderer.contains("RenderSystem.blendFuncSeparate("));
        assertTrue(renderer.contains("if (enabled)"));
        assertTrue(renderer.contains("RenderSystem.enableBlend();"));
        assertTrue(renderer.contains("RenderSystem.disableBlend();"));
        assertTrue(color < blit);
        assertTrue(blit < finallyBlock);
        assertTrue(finallyBlock < resetColor);
        assertFalse(renderer.contains("GameRenderer::getPositionTexShader"));

        String implicitPath = renderer.substring(
                renderer.indexOf("private static void withImplicitAlphaBlend"),
                renderer.indexOf("public static void withAlphaBlend")
        );
        assertFalse(implicitPath.contains("RenderSystem.disableBlend()"));
        assertTrue(renderer.contains("public static final class AlphaBlendScope implements AutoCloseable"));
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

    @Test
    void tacticalFoundationIsClientOnlyAccessibleAndIntegrated() throws IOException {
        Path ui = CLIENT.resolve("ui");
        String screen = source("TabletScreen.java");
        String button = Files.readString(ui.resolve("widget/TacticalButton.java"));
        String iconButton = Files.readString(ui.resolve("widget/TacticalIconButton.java"));
        String textField = Files.readString(ui.resolve("widget/TacticalTextField.java"));
        String card = Files.readString(ui.resolve("widget/TacticalCard.java"));
        String dialog = Files.readString(ui.resolve("widget/TacticalDialog.java"));
        String tacticalUi = Files.readString(ui.resolve("TacticalUi.java"));

        assertTrue(button.contains("extends Button"));
        assertTrue(iconButton.contains("extends TacticalButton"));
        assertTrue(textField.contains("extends EditBox"));
        assertTrue(textField.contains("ScissorScope.open("));
        assertFalse(textField.contains("enableScissor("));
        assertTrue(card.contains("extends Button"));
        assertTrue(dialog.contains("extends Screen"));
        assertTrue(tacticalUi.contains("ScissorScope.open"));
        assertTrue(tacticalUi.contains("ThreadLocal<Deque<UiFrameContext>>"));

        assertTrue(screen.contains("TacticalUi.openFrame("));
        assertTrue(screen.contains("GuiTextureRenderer.openAlphaBlend(g)"));
        assertFalse(screen.contains("RenderSystem.disableScissor()"));
        assertTrue(screen.contains("TacticalUi.drawPanel"));
        assertTrue(screen.contains("new TacticalTextField"));
        assertTrue(screen.contains("extends TacticalButton"));
        assertFalse(screen.contains("net.minecraft.client.gui.components.EditBox"));

        for (String source : List.of(button, iconButton, textField, card, dialog, tacticalUi)) {
            assertFalse(source.contains("TODO"));
            assertFalse(source.contains("System.nanoTime"));
            assertFalse(source.contains("new Thread("));
        }
    }

    @Test
    void navigationRailParticipatesInVanillaFocusAndNarration() throws IOException {
        String screen = source("TabletScreen.java");
        String navigation = source("TabletNavigationRail.java");

        assertTrue(screen.contains("navigationRail.initialize("));
        assertTrue(screen.contains("this.addWidget(button)"));
        assertTrue(screen.contains("navigationRail.moveFocus(keyCode, getFocused())"));
        assertFalse(screen.contains("navigationRail.mouseClicked("));
        assertTrue(navigation.contains("implements FocusKeyProvider"));
        assertTrue(navigation.contains("TacticalUi.drawFocusRing"));
    }

    @Test
    void actionGridParticipatesInVanillaFocusNarrationAndKeyboardNavigation() throws IOException {
        String screen = source("TabletScreen.java");
        String grid = source("ScrollableActionGrid.java");

        assertTrue(screen.contains("actionGrid.initialize("));
        assertTrue(screen.contains("actionGrid.moveFocus(keyCode, getFocused())"));
        assertTrue(screen.contains("this::actionNarration"));
        assertFalse(screen.contains("actionGrid.mouseClicked("));
        assertTrue(grid.contains("setMessage(narration.apply(item))"));
        assertTrue(grid.contains("TacticalUi.drawFocusRing"));
    }

    @Test
    void profileAndClanViewportStateAreSeparatedFromTheMonolithicScreen() throws IOException {
        String screen = source("TabletScreen.java");
        String state = source("TabletPageState.java");
        String viewport = source("TabletDataViewport.java");
        String profile = source("TabletProfileView.java");

        assertTrue(screen.contains("TabletPageState pageState"));
        assertTrue(screen.contains("TabletProfileView.render"));
        assertTrue(screen.contains("TabletDataViewport.visibleRange"));
        assertFalse(screen.contains("private int infoScroll"));
        assertFalse(screen.contains("private int clanScrollOffset"));
        assertFalse(screen.contains("private int selectedClanIndex"));
        assertTrue(state.contains("Map<String, Integer> offsets"));
        assertTrue(viewport.contains("record VisibleRange"));
        assertTrue(profile.contains("record Model"));
        assertFalse(screen.contains("enableScissor"));
        assertFalse(screen.contains("disableScissor"));
    }

    @Test
    void tabletSteadyStateUsesReloadAwareResourcePresenceCache() throws IOException {
        String screen = source("TabletScreen.java");
        String maps = source("MapVotingScreen.java");
        String cache = source("ClientResourcePresenceCache.java");

        assertTrue(screen.contains("ClientResourcePresenceCache::exists"));
        assertTrue(maps.contains("ClientResourcePresenceCache.exists(candidate)"));
        assertFalse(screen.contains("getResource("));
        assertFalse(maps.contains("getResource("));
        assertTrue(cache.contains("ConcurrentHashMap"));
        assertTrue(cache.contains("RegisterClientReloadListenersEvent"));
        assertTrue(cache.contains("ignored -> clear()"));
    }

    @Test
    void purchaseUnlockAndUpgradeUseTheSafeSharedDialog() throws IOException {
        String screen = source("TabletScreen.java");
        String dialog = Files.readString(CLIENT.resolve("ui/widget/TacticalDialog.java"));

        assertTrue(screen.contains("showActionConfirmation"));
        assertTrue(screen.contains("new TacticalDialog("));
        assertFalse(screen.contains("new TabletConfirmScreen("));
        assertTrue(dialog.contains("BooleanSupplier confirmEnabled"));
        assertTrue(dialog.contains("confirmButton.active = confirmEnabled.getAsBoolean()"));
    }

    @Test
    void clanActionsUsePermissionPolicyAndSafeSharedDialogs() throws IOException {
        String screen = source("TabletScreen.java");
        String policy = source("ClanPagePolicy.java");
        String input = source("ClanCreateInputPolicy.java");

        assertTrue(screen.contains("ClanPagePolicy.permissions("));
        assertTrue(screen.contains("openClanCreateConfirmation"));
        assertTrue(screen.contains("openClanJoinConfirmation"));
        assertTrue(screen.contains("openClanConfirmation("));
        assertFalse(screen.contains("new ClanCreateConfirmScreen("));
        assertFalse(screen.contains("new ClanJoinConfirmScreen("));
        assertFalse(screen.contains("new ClanSimpleConfirmScreen("));
        assertTrue(screen.contains("setInitialFocus(nameBox)"));
        assertTrue(policy.contains("record Permissions"));
        assertTrue(input.contains("coins >= ClanConstants.CREATE_COST"));
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
