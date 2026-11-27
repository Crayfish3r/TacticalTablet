package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.render.ScissorScope;
import com.makar.tacticaltablet.tablet.client.ui.widget.FocusKeyProvider;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** Row-scrolling action grid backed by vanilla-compatible focusable widgets. */
public final class ScrollableActionGrid<T> {
    public static final int WIDTH = 270;
    public static final int HEIGHT = 150;
    public static final int SCROLLBAR_X = 271;
    public static final int SCROLLBAR_WIDTH = 3;
    public static final int SCROLLBAR_HEIGHT = 148;

    private final Map<String, Integer> scrollRowsBySection = new HashMap<>();
    private final CardRenderer<T> renderer;
    private final Consumer<T> onPress;
    private final Function<T, Component> narration;
    private final List<GridButton> buttons = new ArrayList<>();
    private String section = "";
    private List<T> items = List.of();
    private int x;
    private int y;

    public ScrollableActionGrid(
            CardRenderer<T> renderer,
            Consumer<T> onPress,
            Function<T, Component> narration
    ) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.onPress = Objects.requireNonNull(onPress, "onPress");
        this.narration = Objects.requireNonNull(narration, "narration");
    }

    public void initialize(
            int x,
            int y,
            String section,
            List<T> items,
            Consumer<Button> widgetRegistrar
    ) {
        this.x = x;
        this.y = y;
        this.section = Objects.requireNonNull(section, "section");
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        setScrollRows(scrollRows());
        buttons.clear();
        for (int index = 0; index < this.items.size(); index++) {
            T item = this.items.get(index);
            GridButton button = new GridButton(index, item, narration.apply(item), onPress);
            buttons.add(button);
            widgetRegistrar.accept(button);
        }
        layoutButtons();
    }

    public int scrollRows() {
        return scrollRowsBySection.getOrDefault(section, 0);
    }

    public void setScrollRows(int rows) {
        scrollRowsBySection.put(section, ScrollableGridLayout.clampScrollRows(rows, items.size()));
        layoutButtons();
    }

    public Button moveFocus(int keyCode, GuiEventListener focused) {
        if (!(focused instanceof ScrollableActionGrid<?>.GridButton current)) return null;
        int direction = switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> ScrollableGridLayout.LEFT;
            case GLFW.GLFW_KEY_RIGHT -> ScrollableGridLayout.RIGHT;
            case GLFW.GLFW_KEY_UP -> ScrollableGridLayout.UP;
            case GLFW.GLFW_KEY_DOWN -> ScrollableGridLayout.DOWN;
            default -> -1;
        };
        if (direction < 0 || buttons.isEmpty()) return null;
        int next = ScrollableGridLayout.moveIndex(current.index, buttons.size(), direction);
        reveal(next);
        return buttons.get(next);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (ScissorScope ignored = ScissorScope.open(graphics, x, y, WIDTH, HEIGHT)) {
            for (GridButton button : buttons) {
                if (button.visible) button.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        renderScrollbar(graphics);
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

    private void reveal(int index) {
        setScrollRows(ScrollableGridLayout.scrollRowsToReveal(index, scrollRows(), items.size()));
    }

    private void layoutButtons() {
        if (buttons.isEmpty()) return;
        int scrollRows = scrollRows();
        for (GridButton button : buttons) {
            ScrollableGridLayout.Position position = ScrollableGridLayout.positionForIndex(
                    button.index, x, y, scrollRows);
            button.setX(position.x());
            button.setY(position.y());
            button.visible = ScrollableGridLayout.isVisible(button.index, scrollRows, items.size());
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int maxScroll = ScrollableGridLayout.maxScrollRows(items.size());
        if (maxScroll <= 0) return;

        int trackX = x + SCROLLBAR_X;
        graphics.fill(trackX, y, trackX + SCROLLBAR_WIDTH, y + SCROLLBAR_HEIGHT, TacticalTheme.SURFACE_DISABLED);
        int totalRows = ScrollableGridLayout.rowCount(items.size());
        int thumbHeight = Math.max(18, SCROLLBAR_HEIGHT * ScrollableGridLayout.VISIBLE_ROWS / totalRows);
        int thumbY = y + (SCROLLBAR_HEIGHT - thumbHeight) * scrollRows() / maxScroll;
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, TacticalTheme.ACCENT_MUTED);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @FunctionalInterface
    public interface CardRenderer<T> {
        void render(GuiGraphics graphics, T item, int x, int y, int width, int height,
                    int mouseX, int mouseY, float partialTick, boolean focused);
    }

    private final class GridButton extends Button implements FocusKeyProvider {
        private final int index;
        private final T item;

        private GridButton(int index, T item, Component message, Consumer<T> action) {
            super(Button.builder(message, ignored -> action.accept(item))
                    .bounds(0, 0, ScrollableGridLayout.CARD_WIDTH, ScrollableGridLayout.CARD_HEIGHT));
            this.index = index;
            this.item = item;
        }

        @Override
        public String focusKey() {
            return "tablet.actions." + section + "." + index;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            setMessage(narration.apply(item));
            renderer.render(graphics, item, getX(), getY(), width, height,
                    mouseX, mouseY, partialTick, isFocused());
            if (isFocused()) {
                TacticalUi.drawFocusRing(graphics, getX(), getY(), width, height, TacticalTheme.ACCENT);
            }
        }
    }
}
