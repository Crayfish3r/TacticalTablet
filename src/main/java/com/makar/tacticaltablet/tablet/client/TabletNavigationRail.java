package com.makar.tacticaltablet.tablet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

public final class TabletNavigationRail {
    public static final int WIDTH = 72;
    public static final int HEIGHT = 192;
    public static final int BUTTON_HEIGHT = 28;
    public static final int BUTTON_GAP = 5;
    private static final int VISIBLE_BUTTONS = 5;

    private final List<Item> items;
    private final IntConsumer onSelect;
    private final Runnable onHover;
    private int x;
    private int y;
    private int selectedIndex;
    private int scroll;
    private int lastHoveredIndex = -1;

    public TabletNavigationRail(List<Item> items, IntConsumer onSelect, Runnable onHover) {
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
        this.onHover = Objects.requireNonNull(onHover, "onHover");
    }

    public void setBounds(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = Math.max(0, Math.min(items.size() - 1, selectedIndex));
        if (this.selectedIndex < scroll) scroll = this.selectedIndex;
        if (this.selectedIndex >= scroll + VISIBLE_BUTTONS) scroll = this.selectedIndex - VISIBLE_BUTTONS + 1;
        clampScroll();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        int hoveredIndex = -1;
        int end = Math.min(items.size(), scroll + VISIBLE_BUTTONS);
        for (int index = scroll; index < end; index++) {
            Item item = items.get(index);
            int buttonY = y + (index - scroll) * (BUTTON_HEIGHT + BUTTON_GAP);
            boolean hovered = inside(mouseX, mouseY, x, buttonY, WIDTH, BUTTON_HEIGHT);
            if (hovered) hoveredIndex = index;
            boolean selected = selectedIndex == index;
            ButtonTextureSpec texture = item.textures().select(true, selected, hovered);
            GuiTextureRenderer.blitWithAlpha(graphics, texture, x, buttonY, WIDTH, BUTTON_HEIGHT);
            int color = selected || hovered ? 0xFFE6F0E8 : 0xFF9FB2A4;
            graphics.drawCenteredString(Minecraft.getInstance().font, item.label(),
                    x + WIDTH / 2, buttonY + 10, color);
        }
        if (hoveredIndex >= 0 && hoveredIndex != lastHoveredIndex && hoveredIndex != selectedIndex) onHover.run();
        lastHoveredIndex = hoveredIndex;

        if (scroll > 0) graphics.drawCenteredString(Minecraft.getInstance().font, "▲", x + WIDTH / 2, y - 8, 0xFF9FB2A4);
        if (scroll + VISIBLE_BUTTONS < items.size()) {
            graphics.drawCenteredString(Minecraft.getInstance().font, "▼", x + WIDTH / 2, y + HEIGHT - 8, 0xFF9FB2A4);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int index = indexAt(mouseX, mouseY);
        if (index < 0) return false;
        if (index != selectedIndex) onSelect.accept(index);
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!inside(mouseX, mouseY, x, y, WIDTH, HEIGHT) || items.size() <= VISIBLE_BUTTONS || delta == 0.0D) {
            return false;
        }
        int before = scroll;
        scroll += delta > 0.0D ? -1 : 1;
        clampScroll();
        return before != scroll;
    }

    private int indexAt(double mouseX, double mouseY) {
        int end = Math.min(items.size(), scroll + VISIBLE_BUTTONS);
        for (int index = scroll; index < end; index++) {
            int buttonY = y + (index - scroll) * (BUTTON_HEIGHT + BUTTON_GAP);
            if (inside(mouseX, mouseY, x, buttonY, WIDTH, BUTTON_HEIGHT)) return index;
        }
        return -1;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(Math.max(0, items.size() - VISIBLE_BUTTONS), scroll));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public record Item(String label, ButtonTextureSet textures) {
        public Item {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(textures, "textures");
        }
    }
}
