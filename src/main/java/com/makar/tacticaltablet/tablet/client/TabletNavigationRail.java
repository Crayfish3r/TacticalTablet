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

    private final List<String> labels;
    private final IntConsumer onSelect;
    private final Runnable onHover;
    private int x;
    private int y;
    private int selectedIndex;
    private int scroll;
    private int lastHoveredIndex = -1;

    public TabletNavigationRail(List<String> labels, IntConsumer onSelect, Runnable onHover) {
        this.labels = List.copyOf(Objects.requireNonNull(labels, "labels"));
        this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
        this.onHover = Objects.requireNonNull(onHover, "onHover");
    }

    public void setBounds(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = Math.max(0, Math.min(labels.size() - 1, selectedIndex));
        if (this.selectedIndex < scroll) scroll = this.selectedIndex;
        if (this.selectedIndex >= scroll + VISIBLE_BUTTONS) scroll = this.selectedIndex - VISIBLE_BUTTONS + 1;
        clampScroll();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        int hoveredIndex = -1;
        int end = Math.min(labels.size(), scroll + VISIBLE_BUTTONS);
        for (int index = scroll; index < end; index++) {
            int buttonY = y + (index - scroll) * (BUTTON_HEIGHT + BUTTON_GAP);
            boolean hovered = inside(mouseX, mouseY, x, buttonY, WIDTH, BUTTON_HEIGHT);
            if (hovered) hoveredIndex = index;
            boolean selected = selectedIndex == index;
            int background = selected ? 0xFF294032 : hovered ? 0xFF223529 : 0xFF18231C;
            graphics.fill(x, buttonY, x + WIDTH, buttonY + BUTTON_HEIGHT, background);
            graphics.fill(x, buttonY + BUTTON_HEIGHT - 1, x + WIDTH, buttonY + BUTTON_HEIGHT, 0xFF496454);
            if (selected) graphics.fill(x, buttonY, x + 2, buttonY + BUTTON_HEIGHT, 0xFF72D68A);
            int color = selected || hovered ? 0xFFE6F0E8 : 0xFF9FB2A4;
            graphics.drawCenteredString(Minecraft.getInstance().font, labels.get(index),
                    x + WIDTH / 2, buttonY + 10, color);
        }
        if (hoveredIndex >= 0 && hoveredIndex != lastHoveredIndex && hoveredIndex != selectedIndex) onHover.run();
        lastHoveredIndex = hoveredIndex;

        if (scroll > 0) graphics.drawCenteredString(Minecraft.getInstance().font, "▲", x + WIDTH / 2, y - 8, 0xFF9FB2A4);
        if (scroll + VISIBLE_BUTTONS < labels.size()) {
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
        if (!inside(mouseX, mouseY, x, y, WIDTH, HEIGHT) || labels.size() <= VISIBLE_BUTTONS || delta == 0.0D) {
            return false;
        }
        int before = scroll;
        scroll += delta > 0.0D ? -1 : 1;
        clampScroll();
        return before != scroll;
    }

    private int indexAt(double mouseX, double mouseY) {
        int end = Math.min(labels.size(), scroll + VISIBLE_BUTTONS);
        for (int index = scroll; index < end; index++) {
            int buttonY = y + (index - scroll) * (BUTTON_HEIGHT + BUTTON_GAP);
            if (inside(mouseX, mouseY, x, buttonY, WIDTH, BUTTON_HEIGHT)) return index;
        }
        return -1;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(Math.max(0, labels.size() - VISIBLE_BUTTONS), scroll));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
