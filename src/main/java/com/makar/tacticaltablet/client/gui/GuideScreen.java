package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameContext;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class GuideScreen extends Screen implements com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider {

    private static final int MAX_PANEL_WIDTH = 760;
    private static final int MAX_PANEL_HEIGHT = 500;
    private static final int SCREEN_MARGIN = 12;
    private static final int PANEL_PADDING = 12;
    private static final int TAB_GAP = 6;
    private static final int SCROLL_STEP = 28;

    private final Screen parent;
    private final UiFrameClock frameClock = new UiFrameClock();
    private final List<RenderedLine> renderedLines = new ArrayList<>();

    private InformationContent.Section selectedSection = InformationContent.Section.SERVER;
    private Layout layout;
    private int contentHeight;
    private int scrollOffset;
    private int maxScroll;

    public GuideScreen(Screen parent) {
        super(Component.translatable("screen.tacticaltablet.guide.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout = Layout.calculate(width, height);
        int tabWidth = Math.max(1,
                (layout.contentWidth() - TAB_GAP * (InformationContent.Section.values().length - 1))
                        / InformationContent.Section.values().length);
        int tabX = layout.contentX();

        for (InformationContent.Section section : InformationContent.Section.values()) {
            InformationContent.Section target = section;
            TacticalButton button = TacticalButton.compact(
                            tabX,
                            layout.tabsY(),
                            tabWidth,
                            section.title(),
                            ignored -> selectSection(target)
                    )
                    .selectedWhen(() -> selectedSection == target)
                    .withAccentBar(true)
                    .withFocusKey("information." + section.name().toLowerCase());
            addRenderableWidget(button);
            tabX += tabWidth + TAB_GAP;
        }

        int backWidth = Math.min(180, layout.contentWidth());
        addRenderableWidget(TacticalButton.compact(
                        layout.contentX() + (layout.contentWidth() - backWidth) / 2,
                        layout.backY(),
                        backWidth,
                        Component.translatable("screen.tacticaltablet.common.back"),
                        ignored -> onClose()
                )
                .withFocusKey("information.back"));

        rebuildContent();
    }

    private void selectSection(InformationContent.Section section) {
        if (section == selectedSection) return;
        selectedSection = section;
        scrollOffset = 0;
        rebuildContent();
    }

    private void rebuildContent() {
        renderedLines.clear();
        int cursorY = 0;
        int wrapWidth = Math.max(40, layout.viewportWidth() - PANEL_PADDING - 5);

        for (InformationContent.Block block : InformationContent.blocks(selectedSection)) {
            BlockMetrics metrics = metrics(block.style());
            cursorY += metrics.spacingBefore();
            List<FormattedCharSequence> wrapped = font.split(block.text(), wrapWidth);
            if (wrapped.isEmpty()) {
                cursorY += metrics.spacingAfter();
                continue;
            }
            for (FormattedCharSequence line : wrapped) {
                renderedLines.add(new RenderedLine(
                        line,
                        cursorY,
                        metrics.color(),
                        metrics.centered()
                ));
                cursorY += font.lineHeight + metrics.lineSpacing();
            }
            cursorY += metrics.spacingAfter();
        }

        contentHeight = Math.max(0, cursorY);
        maxScroll = Math.max(0, contentHeight - layout.viewportHeight());
        scrollOffset = clamp(scrollOffset, 0, maxScroll);
    }

    private static BlockMetrics metrics(InformationContent.BlockStyle style) {
        return switch (style) {
            case TITLE -> new BlockMetrics(TacticalUi.currentPalette().accent(), 0, 5, 2, true);
            case HEADING -> new BlockMetrics(TacticalUi.currentPalette().info(), 8, 2, 1, false);
            case BODY -> new BlockMetrics(TacticalUi.currentPalette().textPrimary(), 1, 5, 1, false);
            case BULLET -> new BlockMetrics(TacticalUi.currentPalette().textSecondary(), 0, 3, 1, false);
            case WARNING -> new BlockMetrics(TacticalUi.currentPalette().warning(), 4, 6, 1, false);
        };
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiFrameContext frame = frameClock.nextFrame(Util.getMillis(), reducedMotion());
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame,
                com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE)) {
            renderBackdrop(graphics);
            TacticalUi.drawPanel(
                    graphics,
                    layout.panelX(),
                    layout.panelY(),
                    layout.panelWidth(),
                    layout.panelHeight()
            );
            graphics.drawCenteredString(
                    font,
                    title,
                    width / 2,
                    layout.titleY(),
                    TacticalUi.currentPalette().textPrimary()
            );
            TacticalUi.drawDivider(
                    graphics,
                    layout.contentX(),
                    layout.dividerY(),
                    layout.contentWidth(),
                    false
            );
            renderContent(graphics);
            renderScrollbar(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderBackdrop(GuiGraphics graphics) {
        if (minecraft.level == null) {
            float coverScale = Math.max(
                    width / (float) MenuTextureSet.BACKGROUND_WIDTH,
                    height / (float) MenuTextureSet.BACKGROUND_HEIGHT
            );
            int drawWidth = Math.max(1, (int) Math.ceil(MenuTextureSet.BACKGROUND_WIDTH * coverScale));
            int drawHeight = Math.max(1, (int) Math.ceil(MenuTextureSet.BACKGROUND_HEIGHT * coverScale));
            graphics.blit(
                    MenuTextureSet.BACKGROUND,
                    (width - drawWidth) / 2,
                    (height - drawHeight) / 2,
                    drawWidth,
                    drawHeight,
                    0.0F,
                    0.0F,
                    MenuTextureSet.BACKGROUND_WIDTH,
                    MenuTextureSet.BACKGROUND_HEIGHT,
                    MenuTextureSet.BACKGROUND_WIDTH,
                    MenuTextureSet.BACKGROUND_HEIGHT
            );
        }
        TacticalUi.drawBackdrop(graphics, width, height);
    }

    private void renderContent(GuiGraphics graphics) {
        TacticalUi.withScissor(
                graphics,
                layout.viewportX(),
                layout.viewportY(),
                layout.viewportWidth(),
                layout.viewportHeight(),
                () -> {
                    for (RenderedLine line : renderedLines) {
                        int y = layout.viewportY() + line.y() - scrollOffset;
                        if (y + font.lineHeight < layout.viewportY()
                                || y > layout.viewportY() + layout.viewportHeight()) {
                            continue;
                        }
                        int x = layout.viewportX() + PANEL_PADDING / 2;
                        if (line.centered()) {
                            x = layout.viewportX()
                                    + (layout.viewportWidth() - font.width(line.text())) / 2;
                        }
                        graphics.drawString(font, line.text(), x, y, line.color(), false);
                    }
                }
        );
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) return;
        int trackX = layout.viewportX() + layout.viewportWidth() - 3;
        int trackHeight = layout.viewportHeight();
        int thumbHeight = Math.max(14,
                Math.round(trackHeight * (trackHeight / (float) Math.max(trackHeight, contentHeight))));
        int travel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = layout.viewportY()
                + Math.round(travel * (scrollOffset / (float) maxScroll));

        graphics.fill(trackX, layout.viewportY(), trackX + 2,
                layout.viewportY() + trackHeight, TacticalUi.currentPalette().borderDisabled());
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, TacticalUi.currentPalette().accentMuted());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (layout != null && layout.isInsideViewport(mouseX, mouseY) && maxScroll > 0) {
            scrollOffset = clamp(
                    scrollOffset - (int) Math.round(delta * SCROLL_STEP),
                    0,
                    maxScroll
            );
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int page = Math.max(SCROLL_STEP, layout == null ? SCROLL_STEP : layout.viewportHeight() - 20);
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> scrollBy(-SCROLL_STEP);
            case GLFW.GLFW_KEY_DOWN -> scrollBy(SCROLL_STEP);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-page);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(page);
            case GLFW.GLFW_KEY_HOME -> scrollOffset = 0;
            case GLFW.GLFW_KEY_END -> scrollOffset = maxScroll;
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    private void scrollBy(int amount) {
        scrollOffset = clamp(scrollOffset + amount, 0, maxScroll);
    }

    private boolean reducedMotion() {
        return minecraft != null && minecraft.options.screenEffectScale().get() <= 0.0D;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record RenderedLine(
            FormattedCharSequence text,
            int y,
            int color,
            boolean centered
    ) {
    }

    private record BlockMetrics(
            int color,
            int spacingBefore,
            int spacingAfter,
            int lineSpacing,
            boolean centered
    ) {
    }

    @Override
    public com.makar.tacticaltablet.tablet.client.ui.UiPalette uiPalette() {
        return com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE;
    }

    private record Layout(
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int contentX,
            int contentWidth,
            int titleY,
            int tabsY,
            int dividerY,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            int backY
    ) {
        private static Layout calculate(int screenWidth, int screenHeight) {
            int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(280, screenWidth - SCREEN_MARGIN * 2));
            int panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(190, screenHeight - SCREEN_MARGIN * 2));
            panelWidth = Math.min(panelWidth, Math.max(1, screenWidth));
            panelHeight = Math.min(panelHeight, Math.max(1, screenHeight));

            int panelX = (screenWidth - panelWidth) / 2;
            int panelY = (screenHeight - panelHeight) / 2;
            int contentX = panelX + PANEL_PADDING;
            int contentWidth = Math.max(1, panelWidth - PANEL_PADDING * 2);
            int titleY = panelY + 9;
            int tabsY = panelY + 26;
            int dividerY = tabsY + TacticalTheme.CONTROL_HEIGHT_COMPACT + 5;
            int backY = panelY + panelHeight - PANEL_PADDING - TacticalTheme.CONTROL_HEIGHT_COMPACT;
            int viewportY = dividerY + 6;
            int viewportHeight = Math.max(1, backY - viewportY - 6);

            return new Layout(
                    panelX,
                    panelY,
                    panelWidth,
                    panelHeight,
                    contentX,
                    contentWidth,
                    titleY,
                    tabsY,
                    dividerY,
                    contentX,
                    viewportY,
                    contentWidth,
                    viewportHeight,
                    backY
            );
        }

        private boolean isInsideViewport(double mouseX, double mouseY) {
            return mouseX >= viewportX
                    && mouseX < viewportX + viewportWidth
                    && mouseY >= viewportY
                    && mouseY < viewportY + viewportHeight;
        }
    }
}
