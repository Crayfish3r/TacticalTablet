package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameContext;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class FilteredKeyBindsScreen extends Screen implements com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider {

    private static final int MAX_PANEL_WIDTH = 820;
    private static final int MAX_PANEL_HEIGHT = 540;
    private static final int SCREEN_MARGIN = 10;
    private static final int PANEL_PADDING = 12;
    private static final int SCROLL_STEP = 30;
    private static final int CATEGORY_HEIGHT = 23;
    private static final int ROW_BASE_HEIGHT = 27;
    private static final int DESCRIPTION_LINE_SPACING = 1;
    private static final int BINDING_WIDTH = 154;
    private static final int RESET_WIDTH = 46;
    private static final int ACTION_GAP = 5;

    private final Screen parent;
    private final Options options;
    private final UiFrameClock frameClock = new UiFrameClock();
    private final List<Row> rows = new ArrayList<>();
    private final List<KeyMapping> visibleMappings = new ArrayList<>();
    private Layout layout;
    private TacticalButton resetAllButton;
    private KeyMapping selectedKey;
    private int contentHeight;
    private int scrollOffset;
    private int maxScroll;

    public FilteredKeyBindsScreen(Screen parent, Options options) {
        super(Component.translatable("screen.tacticaltablet.controls.keybinds"));
        this.parent = parent;
        this.options = options;
    }

    @Override
    protected void init() {
        layout = Layout.calculate(width, height);
        rebuildRows();

        int buttonWidth = Math.min(180, Math.max(1, (layout.contentWidth() - ACTION_GAP) / 2));
        int buttonsX = layout.contentX() + (layout.contentWidth() - buttonWidth * 2 - ACTION_GAP) / 2;
        resetAllButton = TacticalButton.compact(
                        buttonsX,
                        layout.bottomButtonsY(),
                        buttonWidth,
                        Component.translatable("controls.resetAll"),
                        ignored -> resetAllVisible()
                )
                .withAccentColor(com.makar.tacticaltablet.client.ExternalUiTheme.WARNING)
                .withFocusKey("keybinds.reset_all");
        addRenderableWidget(resetAllButton);
        addRenderableWidget(TacticalButton.compact(
                        buttonsX + buttonWidth + ACTION_GAP,
                        layout.bottomButtonsY(),
                        buttonWidth,
                        Component.translatable("screen.tacticaltablet.common.back"),
                        ignored -> onClose()
                )
                .withAccentBar(true)
                .withFocusKey("keybinds.back"));
    }

    private void rebuildRows() {
        rows.clear();
        visibleMappings.clear();
        Map<KeyBindingVisibilityPolicy.Group, List<Binding>> grouped =
                new EnumMap<>(KeyBindingVisibilityPolicy.Group.class);

        for (KeyMapping mapping : options.keyMappings) {
            KeyBindingVisibilityPolicy.classify(mapping.getName(), mapping.getCategory())
                    .ifPresent(entry -> {
                        Component name = entry.displayNameKey() == null
                                ? Component.translatable(entry.originalName())
                                : Component.translatable(entry.displayNameKey());
                        Component description = entry.descriptionKey() == null
                                ? null : Component.translatable(entry.descriptionKey());
                        grouped.computeIfAbsent(entry.group(), ignored -> new ArrayList<>())
                                .add(new Binding(mapping, name, description));
                        visibleMappings.add(mapping);
                    });
        }

        int y = 0;
        int descriptionWidth = Math.max(40,
                layout.contentWidth() - BINDING_WIDTH - RESET_WIDTH - ACTION_GAP * 3 - 12);
        for (KeyBindingVisibilityPolicy.Group group : KeyBindingVisibilityPolicy.Group.values()) {
            List<Binding> bindings = grouped.get(group);
            if (bindings == null || bindings.isEmpty()) continue;
            rows.add(Row.category(y, Component.translatable(group.titleKey())));
            y += CATEGORY_HEIGHT;
            for (Binding binding : bindings) {
                List<FormattedCharSequence> descriptionLines = binding.description() == null
                        ? List.of()
                        : font.split(binding.description(), descriptionWidth);
                int height = ROW_BASE_HEIGHT;
                if (!descriptionLines.isEmpty()) {
                    height += descriptionLines.size() * (font.lineHeight + DESCRIPTION_LINE_SPACING) + 2;
                }
                rows.add(Row.binding(y, height, binding, descriptionLines));
                y += height + 3;
            }
            y += 5;
        }
        contentHeight = Math.max(0, y);
        maxScroll = Math.max(0, contentHeight - layout.viewportHeight());
        scrollOffset = clamp(scrollOffset, 0, maxScroll);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiFrameContext frame = frameClock.nextFrame(Util.getMillis(), reducedMotion());
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame,
                com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE)) {
            TacticalScreenBackground.render(graphics, minecraft, width, height);
            TacticalUi.drawPanel(graphics, layout.panelX(), layout.panelY(),
                    layout.panelWidth(), layout.panelHeight());
            graphics.drawCenteredString(font, title, width / 2, layout.titleY(),
                    TacticalUi.currentPalette().textPrimary());
            TacticalUi.drawDivider(graphics, layout.contentX(), layout.dividerY(),
                    layout.contentWidth(), false);
            renderRows(graphics, mouseX, mouseY);
            renderScrollbar(graphics);
            resetAllButton.active = visibleMappings.stream().anyMatch(mapping -> !mapping.isDefault());
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        TacticalUi.withScissor(graphics, layout.viewportX(), layout.viewportY(),
                layout.viewportWidth(), layout.viewportHeight(), () -> {
                    for (Row row : rows) {
                        int y = layout.viewportY() + row.y() - scrollOffset;
                        if (y + row.height() < layout.viewportY()
                                || y > layout.viewportY() + layout.viewportHeight()) continue;
                        if (row.categoryTitle() != null) {
                            graphics.drawString(font, row.categoryTitle(), layout.contentX() + 4,
                                    y + 7, TacticalUi.currentPalette().accent(), false);
                            TacticalUi.drawDivider(graphics, layout.contentX() + 4,
                                    y + CATEGORY_HEIGHT - 2, layout.contentWidth() - 8, false);
                        } else {
                            renderBindingRow(graphics, row, y, mouseX, mouseY);
                        }
                    }
                });
    }

    private void renderBindingRow(GuiGraphics graphics, Row row, int y, int mouseX, int mouseY) {
        Binding binding = row.binding();
        int rowX = layout.contentX() + 2;
        int rowWidth = layout.contentWidth() - 6;
        boolean rowHovered = inside(mouseX, mouseY, rowX, y, rowWidth, row.height());
        TacticalUi.drawCutCornerBorder(graphics, rowX, y, rowWidth, row.height(),
                TacticalTheme.CORNER_CUT, 1,
                rowHovered ? TacticalUi.currentPalette().borderHover() : TacticalUi.currentPalette().border(),
                rowHovered ? TacticalUi.currentPalette().surfaceHover() : TacticalUi.currentPalette().surfaceRaised());

        int actionY = y + 3;
        int actionHeight = TacticalTheme.CONTROL_HEIGHT_COMPACT;
        int resetX = layout.contentX() + layout.contentWidth() - RESET_WIDTH - 5;
        int bindingX = resetX - ACTION_GAP - BINDING_WIDTH;
        int labelX = rowX + 8;
        graphics.drawString(font, binding.name(), labelX, y + 6,
                TacticalUi.currentPalette().textPrimary(), false);
        int descriptionY = y + 6 + font.lineHeight + 2;
        for (FormattedCharSequence line : row.descriptionLines()) {
            graphics.drawString(font, line, labelX, descriptionY,
                    TacticalUi.currentPalette().textSecondary(), false);
            descriptionY += font.lineHeight + DESCRIPTION_LINE_SPACING;
        }

        boolean bindingHovered = inside(mouseX, mouseY, bindingX, actionY,
                BINDING_WIDTH, actionHeight);
        boolean resetHovered = inside(mouseX, mouseY, resetX, actionY,
                RESET_WIDTH, actionHeight);
        boolean selected = selectedKey == binding.mapping();
        boolean conflict = hasConflict(binding.mapping());

        drawAction(graphics, bindingX, actionY, BINDING_WIDTH, actionHeight,
                bindingHovered, selected, conflict ? TacticalUi.currentPalette().danger() : TacticalUi.currentPalette().accent());
        Component keyMessage = selected
                ? Component.translatable("screen.tacticaltablet.keybind.press_key")
                : binding.mapping().getTranslatedKeyMessage();
        graphics.drawCenteredString(font, keyMessage, bindingX + BINDING_WIDTH / 2,
                actionY + (actionHeight - font.lineHeight) / 2 + 1,
                conflict ? TacticalUi.currentPalette().danger() : TacticalUi.currentPalette().textPrimary());

        int resetAccent = binding.mapping().isDefault()
                ? TacticalUi.currentPalette().borderDisabled() : TacticalUi.currentPalette().warning();
        drawAction(graphics, resetX, actionY, RESET_WIDTH, actionHeight,
                resetHovered, false, resetAccent);
        graphics.drawCenteredString(font, Component.translatable("controls.reset"),
                resetX + RESET_WIDTH / 2,
                actionY + (actionHeight - font.lineHeight) / 2 + 1,
                binding.mapping().isDefault() ? TacticalUi.currentPalette().textDisabled() : TacticalUi.currentPalette().textPrimary());
    }

    private static void drawAction(GuiGraphics graphics, int x, int y, int width, int height,
                                   boolean hovered, boolean selected, int accent) {
        TacticalUi.ControlVisualState state = new TacticalUi.ControlVisualState(
                true, hovered, false, false, selected
        );
        TacticalUi.drawButton(graphics, x, y, width, height, state,
                hovered ? 1.0F : 0.0F, selected ? 1.0F : 0.0F, accent);
    }

    private boolean hasConflict(KeyMapping mapping) {
        if (mapping.isUnbound()) return false;
        for (KeyMapping other : options.keyMappings) {
            if (other != mapping && mapping.same(other)) return true;
        }
        return false;
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) return;
        int trackX = layout.viewportX() + layout.viewportWidth() - 3;
        int trackHeight = layout.viewportHeight();
        int thumbHeight = Math.max(14,
                Math.round(trackHeight * (trackHeight / (float) Math.max(trackHeight, contentHeight))));
        int travel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = layout.viewportY() + Math.round(travel * (scrollOffset / (float) maxScroll));
        graphics.fill(trackX, layout.viewportY(), trackX + 2,
                layout.viewportY() + trackHeight, TacticalUi.currentPalette().borderDisabled());
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight,
                TacticalUi.currentPalette().accentMuted());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedKey != null) {
            assignSelected(InputConstants.Type.MOUSE.getOrCreate(button));
            selectedKey = null;
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!layout.insideViewport(mouseX, mouseY)) return false;

        for (Row row : rows) {
            if (row.binding() == null) continue;
            int y = layout.viewportY() + row.y() - scrollOffset;
            if (y + row.height() < layout.viewportY()
                    || y > layout.viewportY() + layout.viewportHeight()) continue;
            int resetX = layout.contentX() + layout.contentWidth() - RESET_WIDTH - 5;
            int bindingX = resetX - ACTION_GAP - BINDING_WIDTH;
            int actionY = y + 3;
            if (inside(mouseX, mouseY, bindingX, actionY,
                    BINDING_WIDTH, TacticalTheme.CONTROL_HEIGHT_COMPACT)) {
                selectedKey = row.binding().mapping();
                return true;
            }
            if (inside(mouseX, mouseY, resetX, actionY,
                    RESET_WIDTH, TacticalTheme.CONTROL_HEIGHT_COMPACT)
                    && !row.binding().mapping().isDefault()) {
                row.binding().mapping().setToDefault();
                KeyMapping.resetMapping();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedKey != null) {
            InputConstants.Key key = keyCode == GLFW.GLFW_KEY_ESCAPE
                    ? InputConstants.UNKNOWN : InputConstants.getKey(keyCode, scanCode);
            selectedKey.setKeyModifierAndCode(null, key);
            options.setKey(selectedKey, key);
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || !KeyModifier.isKeyCodeModifier(key)) {
                selectedKey = null;
            }
            KeyMapping.resetMapping();
            return true;
        }

        int page = Math.max(SCROLL_STEP, layout.viewportHeight() - 20);
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

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key released = InputConstants.getKey(keyCode, scanCode);
        if (selectedKey != null && selectedKey.getKey() == released) {
            selectedKey = null;
            KeyMapping.resetMapping();
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (layout.insideViewport(mouseX, mouseY) && maxScroll > 0) {
            scrollBy(-(int) Math.round(delta * SCROLL_STEP));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void assignSelected(InputConstants.Key key) {
        selectedKey.setKeyModifierAndCode(null, key);
        options.setKey(selectedKey, key);
        KeyMapping.resetMapping();
    }

    private void resetAllVisible() {
        for (KeyMapping mapping : visibleMappings) mapping.setToDefault();
        KeyMapping.resetMapping();
    }

    private void scrollBy(int amount) {
        scrollOffset = clamp(scrollOffset + amount, 0, maxScroll);
    }

    @Override
    public void onClose() {
        options.save();
        Minecraft.getInstance().setScreen(parent);
    }

    private boolean reducedMotion() {
        return minecraft != null && minecraft.options.screenEffectScale().get() <= 0.0D;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Binding(KeyMapping mapping, Component name, Component description) {
    }

    private record Row(int y, int height, Component categoryTitle, Binding binding,
                       List<FormattedCharSequence> descriptionLines) {
        private static Row category(int y, Component title) {
            return new Row(y, CATEGORY_HEIGHT, title, null, List.of());
        }

        private static Row binding(int y, int height, Binding binding,
                                   List<FormattedCharSequence> descriptionLines) {
            return new Row(y, height, null, binding, List.copyOf(descriptionLines));
        }
    }

    @Override
    public com.makar.tacticaltablet.tablet.client.ui.UiPalette uiPalette() {
        return com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE;
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int contentX, int contentWidth, int titleY, int dividerY,
                          int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                          int bottomButtonsY) {
        private static Layout calculate(int screenWidth, int screenHeight) {
            int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(300, screenWidth - SCREEN_MARGIN * 2));
            int panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(210, screenHeight - SCREEN_MARGIN * 2));
            panelWidth = Math.min(panelWidth, Math.max(1, screenWidth));
            panelHeight = Math.min(panelHeight, Math.max(1, screenHeight));
            int panelX = (screenWidth - panelWidth) / 2;
            int panelY = (screenHeight - panelHeight) / 2;
            int contentX = panelX + PANEL_PADDING;
            int contentWidth = Math.max(1, panelWidth - PANEL_PADDING * 2);
            int titleY = panelY + 9;
            int dividerY = panelY + 27;
            int viewportY = dividerY + 6;
            int bottomButtonsY = panelY + panelHeight - PANEL_PADDING
                    - TacticalTheme.CONTROL_HEIGHT_COMPACT;
            int viewportHeight = Math.max(1, bottomButtonsY - viewportY - 7);
            return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, contentWidth,
                    titleY, dividerY, contentX, viewportY, contentWidth, viewportHeight,
                    bottomButtonsY);
        }

        private boolean insideViewport(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, viewportX, viewportY, viewportWidth, viewportHeight);
        }
    }
}
