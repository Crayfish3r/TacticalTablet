package com.makar.tacticaltablet.tablet.client;

import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Widget-free, row-scrolling grid. Only visible cards are rendered and hit-tested.
 */
public final class ScrollableActionGrid<T> {
    public static final int WIDTH = 270;
    public static final int HEIGHT = 150;
    public static final int SCROLLBAR_X = 271;
    public static final int SCROLLBAR_WIDTH = 3;
    public static final int SCROLLBAR_HEIGHT = 148;

    private final Map<String, Integer> scrollRowsBySection = new HashMap<>();
    private final CardRenderer<T> renderer;
    private final Consumer<T> onPress;
    private String section = "";
    private List<T> items = List.of();
    private int x;
    private int y;

    public ScrollableActionGrid(CardRenderer<T> renderer, Consumer<T> onPress) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.onPress = Objects.requireNonNull(onPress, "onPress");
    }

    public void setBounds(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSection(String section, List<T> items) {
        this.section = Objects.requireNonNull(section, "section");
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        setScrollRows(scrollRows());
    }

    public int scrollRows() {
        return scrollRowsBySection.getOrDefault(section, 0);
    }

    public void setScrollRows(int rows) {
        scrollRowsBySection.put(section, ScrollableGridLayout.clampScrollRows(rows, items.size()));
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.enableScissor(x, y, x + WIDTH, y + HEIGHT);
        try {
            int scrollRows = scrollRows();
            for (int index = 0; index < items.size(); index++) {
                if (!ScrollableGridLayout.isVisible(index, scrollRows, items.size())) continue;
                ScrollableGridLayout.Position position = ScrollableGridLayout.positionForIndex(index, x, y, scrollRows);
                renderer.render(graphics, items.get(index), position.x(), position.y(),
                        ScrollableGridLayout.CARD_WIDTH, ScrollableGridLayout.CARD_HEIGHT,
                        mouseX, mouseY, partialTick);
            }
        } finally {
            graphics.disableScissor();
        }

        renderScrollbar(graphics);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !inside(mouseX, mouseY, x, y, WIDTH, HEIGHT)) return false;
        Optional<T> hovered = itemAt(mouseX, mouseY);
        hovered.ifPresent(onPress);
        return hovered.isPresent();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!inside(mouseX, mouseY, x, y, WIDTH + SCROLLBAR_WIDTH + 1, HEIGHT) || delta == 0.0D) {
            return false;
        }
        int direction = delta > 0.0D ? -1 : 1;
        int before = scrollRows();
        setScrollRows(before + direction);
        return scrollRows() != before;
    }

    public Optional<T> itemAt(double mouseX, double mouseY) {
        int scrollRows = scrollRows();
        for (int index = 0; index < items.size(); index++) {
            if (!ScrollableGridLayout.isVisible(index, scrollRows, items.size())) continue;
            ScrollableGridLayout.Position position = ScrollableGridLayout.positionForIndex(index, x, y, scrollRows);
            if (inside(mouseX, mouseY, position.x(), position.y(),
                    ScrollableGridLayout.CARD_WIDTH, ScrollableGridLayout.CARD_HEIGHT)) {
                return Optional.of(items.get(index));
            }
        }
        return Optional.empty();
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int maxScroll = ScrollableGridLayout.maxScrollRows(items.size());
        if (maxScroll <= 0) return;

        int trackX = x + SCROLLBAR_X;
        graphics.fill(trackX, y, trackX + SCROLLBAR_WIDTH, y + SCROLLBAR_HEIGHT, 0x9918231C);
        int totalRows = ScrollableGridLayout.rowCount(items.size());
        int thumbHeight = Math.max(18, SCROLLBAR_HEIGHT * ScrollableGridLayout.VISIBLE_ROWS / totalRows);
        int thumbY = y + (SCROLLBAR_HEIGHT - thumbHeight) * scrollRows() / maxScroll;
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF72D68A);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @FunctionalInterface
    public interface CardRenderer<T> {
        void render(GuiGraphics graphics, T item, int x, int y, int width, int height,
                    int mouseX, int mouseY, float partialTick);
    }
}
